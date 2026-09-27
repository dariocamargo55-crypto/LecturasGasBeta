package com.lecturasgas.beta.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.lecturasgas.beta.data.excel.ExcelReader
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.RouteSegment
import org.json.JSONArray
import org.json.JSONObject

class ReadingViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val prefs =
        app.getSharedPreferences(
            "lecturas_gas",
            Context.MODE_PRIVATE
        )

    var search by
    mutableStateOf("")

    var records by
    mutableStateOf<List<MeterRecord>>(
        emptyList()
    )
        private set

    var imported by
    mutableStateOf(false)
        private set

    var message by
    mutableStateOf<String?>(null)
        private set

    var isBusy by
    mutableStateOf(false)
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

        }
    }

    private fun persist() {

        prefs.edit()
            .putString(
                "records",
                serialize(records)
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

            ExcelReader.copyOriginal(
                app,
                uri
            )

            records =
                parsed

            imported =
                true

            persist()

            message =
                "Importación correcta: ${parsed.size} registros."

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

    fun clearMessage() {
        message = null
    }

    // -------------------------------------------------------------------------
    // BÚSQUEDA
    // -------------------------------------------------------------------------

    val filtered:
            List<MeterRecord>
        get() {

            val query =
                search
                    .trim()
                    .lowercase()

            if (
                query.isEmpty()
            ) {
                return records
            }

            val queryMeter =
                query.filter {
                    it.isLetterOrDigit()
                }

            return records.filter {
                    record ->

                val meter =
                    normalizeMeter(
                        record.meter
                    ).lowercase()

                meter.contains(
                    queryMeter
                ) ||

                        (
                                queryMeter.length == 4 &&
                                        meter.endsWith(
                                            queryMeter
                                        )
                                ) ||

                        record.address
                            .lowercase()
                            .contains(query) ||

                        record.user
                            .lowercase()
                            .contains(query) ||

                        record.neighborhood
                            .lowercase()
                            .contains(query) ||

                        record.nir
                            .lowercase()
                            .contains(query)
            }
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

    val routeSegments:
            List<RouteSegment>
        get() {

            if (records.isEmpty()) {
                return emptyList()
            }

            val segments =
                mutableListOf<RouteSegment>()

            val occurrenceCount =
                mutableMapOf<String, Int>()

            var segmentId = 0

            var currentName =
                records.first()
                    .neighborhood
                    .trim()
                    .ifBlank {
                        "SIN BARRIO"
                    }

            var currentRecords =
                mutableListOf<MeterRecord>()

            records.forEachIndexed { index, record ->

                val name =
                    record.neighborhood
                        .trim()
                        .ifBlank {
                            "SIN BARRIO"
                        }

                if (
                    name != currentName
                ) {

                    val occurrence =
                        occurrenceCount[
                            currentName
                        ] ?: 0

                    occurrenceCount[
                        currentName
                    ] = occurrence + 1

                    val displayName =
                        if (occurrence == 0) {
                            currentName
                        } else {
                            "$currentName ${occurrence + 1}"
                        }

                    segments.add(
                        RouteSegment(
                            id = segmentId,
                            originalName = currentName,
                            displayName = displayName,
                            records = currentRecords.toList()
                        )
                    )

                    segmentId++

                    currentName =
                        name

                    currentRecords =
                        mutableListOf()
                }

                currentRecords.add(
                    record
                )

                if (
                    index ==
                    records.lastIndex
                ) {

                    val occurrence =
                        occurrenceCount[
                            currentName
                        ] ?: 0

                    occurrenceCount[
                        currentName
                    ] = occurrence + 1

                    val displayName =
                        if (occurrence == 0) {
                            currentName
                        } else {
                            "$currentName ${occurrence + 1}"
                        }

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

    val visibleRouteSegments:
            List<RouteSegment>
        get() {

            val query =
                search
                    .trim()
                    .lowercase()

            if (query.isBlank()) {
                return routeSegments
            }

            val matchingRows =
                filtered
                    .map {
                        it.rowNumber
                    }
                    .toSet()

            return routeSegments
                .mapNotNull { segment ->

                    val visibleRecords =
                        segment.records.filter {
                            it.rowNumber in matchingRows
                        }

                    if (
                        visibleRecords.isEmpty()
                    ) {
                        null
                    } else {
                        segment.copy(
                            records =
                                visibleRecords
                        )
                    }
                }
        }

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

    private fun normalizeMeter(
        value: String
    ): String {

        return value
            .trim()
            .removeSuffix(".0")
            .trim()
    }
}