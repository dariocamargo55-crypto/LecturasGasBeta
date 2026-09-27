package com.lecturasgas.beta.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lecturasgas.beta.data.excel.ExcelReader
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.RouteSegment
import org.json.JSONArray
import org.json.JSONObject

enum class SearchMode(val label: String) {
    METER("Medidor"),
    USER("Usuario"),
    ADDRESS("Dirección"),
    NEIGHBORHOOD("Barrio"),
    NIR("NIR"),
    READING("Lecturas"),
    OBSERVATION("Observación"),
    ALL("Todos")
}

class ReadingViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val prefs =
        app.getSharedPreferences(
            "lecturas_gas",
            Context.MODE_PRIVATE
        )

    var search by
    mutableStateOf(
        prefs.getString("search_query", "") ?: ""
    )

    var records by
    mutableStateOf<List<MeterRecord>>(
        emptyList()
    )
        private set

    var imported by
    mutableStateOf(false)
        private set

    var routeName by
    mutableStateOf<String?>(null)
        private set

    var message by
    mutableStateOf<String?>(null)
        private set

    var isBusy by
    mutableStateOf(false)
        private set

    var numericKeyboard by
    mutableStateOf(
        prefs.getBoolean(
            "search_numeric_keyboard",
            true
        )
    )
        private set

    var searchMode by
    mutableStateOf(
        runCatching {
            SearchMode.valueOf(
                prefs.getString(
                    "search_mode",
                    SearchMode.METER.name
                ) ?: SearchMode.METER.name
            )
        }.getOrDefault(SearchMode.METER)
    )
        private set

    private var searchJob: Job? = null

    private var searchIndex: List<SearchEntry> = emptyList()

    var filtered by
    mutableStateOf<List<MeterRecord>>(emptyList())
        private set

    private var cachedRouteSegments: List<RouteSegment> = emptyList()

    var visibleRouteSegments by
    mutableStateOf<List<RouteSegment>>(emptyList())
        private set

    var lastExpandedSegmentId by
    mutableStateOf(
        if (prefs.contains("last_expanded_segment_id")) {
            prefs.getInt("last_expanded_segment_id", -1)
                .takeIf { it >= 0 }
        } else {
            null
        }
    )
        private set

    init {
        loadState()
    }

    // -------------------------------------------------------------------------
    // PERSISTENCIA
    // -------------------------------------------------------------------------

    private fun serialize(
        list: List<MeterRecord>
    ): String {

        val array =
            JSONArray()

        list.forEach { record ->

            array.put(
                JSONObject().apply {

                    put(
                        "row",
                        record.rowNumber
                    )

                    put(
                        "nir",
                        record.nir
                    )

                    put(
                        "address",
                        record.address
                    )

                    put(
                        "meter",
                        record.meter
                    )

                    put(
                        "previous",
                        record.previousReading
                            ?: JSONObject.NULL
                    )

                    put(
                        "neighborhood",
                        record.neighborhood
                    )

                    put(
                        "user",
                        record.user
                    )

                    put(
                        "observation",
                        record.observation
                    )

                    put(
                        "current",
                        record.currentReading
                            ?: JSONObject.NULL
                    )
                }
            )
        }

        return array.toString()
    }

    private fun loadState() {

        val savedRouteName =
            prefs.getString(
                "route_name",
                null
            )

        routeName =
            savedRouteName

        val json =
            prefs.getString(
                "records",
                null
            ) ?: return

        runCatching {

            val array =
                JSONArray(json)

            records =
                List(
                    array.length()
                ) { index ->

                    val obj =
                        array.getJSONObject(
                            index
                        )

                    MeterRecord(

                        rowNumber =
                            obj.getInt("row"),

                        nir =
                            obj.optString(
                                "nir"
                            ),

                        address =
                            obj.optString(
                                "address"
                            ),

                        meter =
                            obj.optString(
                                "meter"
                            ),

                        previousReading =
                            if (
                                obj.isNull(
                                    "previous"
                                )
                            ) {
                                null
                            } else {
                                obj.optLong(
                                    "previous"
                                )
                            },

                        neighborhood =
                            obj.optString(
                                "neighborhood"
                            ),

                        user =
                            obj.optString(
                                "user"
                            ),

                        observation =
                            obj.optString(
                                "observation"
                            ),

                        currentReading =
                            if (
                                obj.isNull(
                                    "current"
                                )
                            ) {
                                null
                            } else {
                                obj.optLong(
                                    "current"
                                )
                            }
                    )
                }

            imported =
                records.isNotEmpty()

            rebuildSearchData()
            applySearchImmediately(search)

        }
    }

    private fun persist() {

        prefs.edit()
            .putString(
                "records",
                serialize(records)
            )
            .putString(
                "route_name",
                routeName
            )
            .putString(
                "search_query",
                search
            )
            .apply()
    }

    // -------------------------------------------------------------------------
    // IMPORTAR EXCEL
    // -------------------------------------------------------------------------

    fun importExcel(
        uri: Uri
    ) {

        isBusy = true

        try {

            val app =
                getApplication<Application>()

            val parsed =
                ExcelReader.read(
                    app,
                    uri
                )

            require(
                parsed.isNotEmpty()
            ) {
                "No se encontraron medidores en el Excel."
            }

            val detectedRouteName =
                ExcelReader.getRouteName(
                    app,
                    uri
                )

            ExcelReader.copyOriginal(
                app,
                uri
            )

            records =
                parsed

            rebuildSearchData()
            applySearchImmediately("")

            routeName =
                detectedRouteName

            imported =
                true

            search = ""
            lastExpandedSegmentId = null

            prefs.edit()
                .remove("last_expanded_segment_id")
                .apply()

            persist()

            message =
                if (
                    routeName.isNullOrBlank()
                ) {
                    "Importación correcta: ${parsed.size} registros."
                } else {
                    "Importación correcta: ${parsed.size} registros.\nRuta: $routeName"
                }

        } catch (e: Exception) {

            message =
                "No se pudo importar: " +
                        (
                                e.message
                                    ?: "error desconocido"
                                )

        } finally {

            isBusy = false
        }
    }

    // -------------------------------------------------------------------------
    // GUARDAR LECTURA
    // -------------------------------------------------------------------------

    fun saveReading(
        rowNumber: Int,
        readingText: String
    ): Boolean {

        val value =
            readingText.toLongOrNull()
                ?: return false

        val record =
            records.firstOrNull {
                it.rowNumber ==
                        rowNumber
            }
                ?: return false

        val previous =
            record.previousReading

        if (
            previous != null &&
            value < previous
        ) {

            message =
                "La lectura $value es menor que la anterior $previous."

            return false
        }

        records =
            records.map {
                    currentRecord ->

                if (
                    currentRecord.rowNumber ==
                    rowNumber
                ) {

                    currentRecord.copy(
                        currentReading =
                            value
                    )

                } else {

                    currentRecord
                }
            }

        rebuildSearchData()
        applySearchImmediately(search)
        persist()

        message =
            "Lectura registrada: $value"

        return true
    }

    // -------------------------------------------------------------------------
    // EXPORTAR EXCEL
    // -------------------------------------------------------------------------

    fun exportExcel(
        uri: Uri
    ) {

        isBusy = true

        try {

            ExcelReader.export(
                getApplication(),
                records,
                uri
            )

            message =
                "Excel exportado correctamente."

        } catch (e: Exception) {

            message =
                "No se pudo exportar: " +
                        (
                                e.message
                                    ?: "error desconocido"
                                )

        } finally {

            isBusy = false
        }
    }

    fun updateNumericKeyboard(enabled: Boolean) {
        numericKeyboard = enabled

        prefs.edit()
            .putBoolean(
                "search_numeric_keyboard",
                enabled
            )
            .apply()
    }

    fun updateSearchMode(mode: SearchMode) {
        searchMode = mode

        prefs.edit()
            .putString(
                "search_mode",
                mode.name
            )
            .apply()

        searchJob?.cancel()

        if (search.isBlank()) {
            applySearchImmediately("")
        } else {
            searchJob = viewModelScope.launch {
                delay(80)
                applySearchImmediately(search)
            }
        }
    }

    fun updateSearch(value: String) {
        search = value

        searchJob?.cancel()

        if (value.isBlank()) {
            applySearchImmediately("")
            prefs.edit()
                .putString("search_query", "")
                .apply()
            return
        }

        searchJob = viewModelScope.launch {
            delay(160)
            applySearchImmediately(value)
            prefs.edit()
                .putString("search_query", value)
                .apply()
        }
    }

    fun updateExpandedSegment(segmentId: Int?) {
        lastExpandedSegmentId = segmentId

        val editor = prefs.edit()

        if (segmentId == null) {
            editor.remove("last_expanded_segment_id")
        } else {
            editor.putInt(
                "last_expanded_segment_id",
                segmentId
            )
        }

        editor.apply()
    }

    fun clearMessage() {
        message = null
    }

    // -------------------------------------------------------------------------
    // BÚSQUEDA OPTIMIZADA
    // -------------------------------------------------------------------------

    private data class SearchEntry(
        val record: MeterRecord,
        val meter: String,
        val meterDigits: String,
        val address: String,
        val user: String,
        val neighborhood: String,
        val nir: String,
        val observation: String,
        val previous: String,
        val current: String
    )

    private fun rebuildSearchData() {
        searchIndex = records.map { record ->
            SearchEntry(
                record = record,
                meter = normalizeMeter(record.meter),
                meterDigits = normalizeMeter(record.meter)
                    .filter(Char::isDigit),
                address = normalizeText(record.address),
                user = normalizeText(record.user),
                neighborhood = normalizeText(record.neighborhood),
                nir = normalizeText(record.nir),
                observation = normalizeText(record.observation),
                previous = record.previousReading?.toString().orEmpty(),
                current = record.currentReading?.toString().orEmpty()
            )
        }

        cachedRouteSegments = buildRouteSegments(records)
    }

    private fun applySearchImmediately(value: String) {
        val normalized = normalizeText(value)

        if (normalized.isEmpty()) {
            filtered = records
            visibleRouteSegments = routeSegments
            return
        }

        val compact = normalized.filter(Char::isLetterOrDigit)
        val digits = normalized.filter(Char::isDigit)

        val queryMeter = normalizeMeter(value)
        val queryMeterDigits = queryMeter.filter(Char::isDigit)

        val scored = searchIndex.mapNotNull { entry ->
            val score = when (searchMode) {
                SearchMode.METER -> scoreMeter(
                    entry,
                    queryMeter,
                    queryMeterDigits,
                    digits,
                    compact
                )

                SearchMode.USER -> scoreTextField(
                    entry.user,
                    normalized
                )

                SearchMode.ADDRESS -> scoreTextField(
                    entry.address,
                    normalized
                )

                SearchMode.NEIGHBORHOOD -> scoreTextField(
                    entry.neighborhood,
                    normalized
                )

                SearchMode.NIR -> scoreTextField(
                    entry.nir,
                    normalized
                )

                SearchMode.READING -> scoreReading(
                    entry,
                    digits,
                    normalized
                )

                SearchMode.OBSERVATION -> scoreTextField(
                    entry.observation,
                    normalized
                )

                SearchMode.ALL -> scoreAll(
                    entry,
                    normalized,
                    compact,
                    digits,
                    queryMeter,
                    queryMeterDigits
                )
            }

            if (score > 0) entry.record to score else null
        }

        filtered = scored
            .sortedByDescending { it.second }
            .map { it.first }

        val matchingRows = filtered.asSequence()
            .map { it.rowNumber }
            .toHashSet()

        visibleRouteSegments = routeSegments.mapNotNull { segment ->
            val visibleRecords = segment.records.filter {
                it.rowNumber in matchingRows
            }

            if (visibleRecords.isEmpty()) null
            else segment.copy(records = visibleRecords)
        }
    }

    private fun scoreMeter(
        entry: SearchEntry,
        queryMeter: String,
        queryMeterDigits: String,
        digits: String,
        compact: String
    ): Int {
        if (queryMeter.isBlank()) return 0

        return when {
            entry.meter == queryMeter -> 1000
            queryMeterDigits.isNotBlank() &&
                    entry.meterDigits == queryMeterDigits -> 950
            digits.length == 4 &&
                    entry.meterDigits.endsWith(digits) -> 900
            compact.isNotEmpty() &&
                    entry.meter.contains(compact) -> 800
            else -> 0
        }
    }

    private fun scoreTextField(
        field: String,
        query: String
    ): Int {
        if (query.isBlank()) return 0

        return when {
            field == query -> 1000
            field.startsWith(query) -> 900
            field.contains(query) -> 800
            else -> 0
        }
    }

    private fun scoreReading(
        entry: SearchEntry,
        digits: String,
        normalized: String
    ): Int {
        if (digits.isBlank() || normalized.any { !it.isDigit() && !it.isWhitespace() }) {
            return 0
        }

        return when {
            entry.previous == digits || entry.current == digits -> 1000
            entry.previous.startsWith(digits) ||
                    entry.current.startsWith(digits) -> 900
            entry.previous.contains(digits) ||
                    entry.current.contains(digits) -> 800
            else -> 0
        }
    }

    private fun scoreAll(
        entry: SearchEntry,
        normalized: String,
        compact: String,
        digits: String,
        queryMeter: String,
        queryMeterDigits: String
    ): Int {
        var score = 0

        if (queryMeter.isNotBlank() && entry.meter == queryMeter) score = maxOf(score, 1000)
        if (queryMeterDigits.isNotBlank() && entry.meterDigits == queryMeterDigits) score = maxOf(score, 950)
        if (digits.length == 4 && entry.meterDigits.endsWith(digits)) score = maxOf(score, 900)
        if (compact.isNotEmpty() && entry.meter.contains(compact)) score = maxOf(score, 800)

        if (digits.isNotBlank()) {
            if (entry.nir.contains(digits)) score = maxOf(score, 700)
            if (entry.previous.contains(digits)) score = maxOf(score, 650)
            if (entry.current.contains(digits)) score = maxOf(score, 650)
        }

        if (entry.address.contains(normalized)) score = maxOf(score, 600)
        if (entry.user.contains(normalized)) score = maxOf(score, 600)
        if (entry.neighborhood.contains(normalized)) score = maxOf(score, 550)
        if (entry.nir.contains(normalized)) score = maxOf(score, 500)
        if (entry.observation.contains(normalized)) score = maxOf(score, 400)

        return score
    }

    // -------------------------------------------------------------------------
    // RESUMEN GENERAL

    // -------------------------------------------------------------------------

    val pendingCount: Int
        get() =
            records.count {
                it.currentReading == null
            }

    val readCount: Int
        get() =
            records.count {
                it.currentReading != null
            }

    // -------------------------------------------------------------------------
    // SEGMENTOS DE RUTA
    // -------------------------------------------------------------------------

    val routeSegments: List<RouteSegment>
        get() = cachedRouteSegments

    private fun buildRouteSegments(
        source: List<MeterRecord>
    ): List<RouteSegment> {

        if (source.isEmpty()) {
            return emptyList()
        }

        val segments = mutableListOf<RouteSegment>()
        val occurrenceCount = mutableMapOf<String, Int>()
        var segmentId = 0
        var currentName = source.first().neighborhood.trim().ifBlank { "SIN BARRIO" }
        var currentRecords = mutableListOf<MeterRecord>()

        source.forEachIndexed { index, record ->
            val name = record.neighborhood.trim().ifBlank { "SIN BARRIO" }

            if (name != currentName) {
                val occurrence = occurrenceCount[currentName] ?: 0
                occurrenceCount[currentName] = occurrence + 1
                val displayName = if (occurrence == 0) currentName else "$currentName ${occurrence + 1}"

                segments.add(
                    RouteSegment(
                        id = segmentId++,
                        originalName = currentName,
                        displayName = displayName,
                        records = currentRecords.toList()
                    )
                )

                currentName = name
                currentRecords = mutableListOf()
            }

            currentRecords.add(record)

            if (index == source.lastIndex) {
                val occurrence = occurrenceCount[currentName] ?: 0
                occurrenceCount[currentName] = occurrence + 1
                val displayName = if (occurrence == 0) currentName else "$currentName ${occurrence + 1}"

                segments.add(
                    RouteSegment(
                        id = segmentId,
                        originalName = currentName,
                        displayName = displayName,
                        records = currentRecords.toList()
                    )
                )
            }
        }

        return segments
    }

    // -------------------------------------------------------------------------
    // SEGMENTOS VISIBLES CON BÚSQUEDA
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // MEDIDORES DUPLICADOS
    // -------------------------------------------------------------------------

    val duplicateMeters:
            Set<String>
        get() =
            records
                .groupingBy {
                    it.meter
                }
                .eachCount()
                .filterValues {
                    it > 1
                }
                .keys

    // -------------------------------------------------------------------------
    // NORMALIZAR MEDIDOR
    // -------------------------------------------------------------------------

    private fun normalizeText(
        value: String
    ): String {
        return java.text.Normalizer
            .normalize(value, java.text.Normalizer.Form.NFD)
            .replace(MARKS_REGEX, "")
            .lowercase()
            .replace(NON_ALNUM_REGEX, " ")
            .trim()
            .replace(SPACES_REGEX, " ")
    }

    private fun normalizeMeter(
        value: String
    ): String {
        val cleaned = value.trim().replace(",", ".")

        return runCatching {
            java.math.BigDecimal(cleaned)
                .stripTrailingZeros()
                .toPlainString()
        }.getOrElse {
            cleaned
        }
    }

    companion object {
        private val MARKS_REGEX = Regex("\\p{M}+")
        private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}]+")
        private val SPACES_REGEX = Regex("\\s+")
    }

}