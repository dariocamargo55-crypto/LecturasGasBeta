package com.lecturasgas.beta

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// -----------------------------------------------------------------------------
// MODELOS
// -----------------------------------------------------------------------------

data class MeterRecord(
    val rowNumber: Int,
    val nir: String,
    val address: String,
    val meter: String,
    val previousReading: Long?,
    val neighborhood: String,
    val user: String,
    val observation: String,
    val currentReading: Long?
)

data class NeighborhoodGroup(
    val name: String,
    val records: List<MeterRecord>
) {
    val total: Int
        get() = records.size

    val read: Int
        get() = records.count {
            it.currentReading != null
        }

    val pending: Int
        get() = records.count {
            it.currentReading == null
        }
}

// -----------------------------------------------------------------------------
// FUNCIONES AUXILIARES
// -----------------------------------------------------------------------------

private fun normalizeMeter(
    value: String
): String {
    return value
        .trim()
        .removeSuffix(".0")
        .trim()
}

private fun parseLongValue(
    value: String
): Long? {

    val cleaned = value.trim()

    if (cleaned.isBlank()) {
        return null
    }

    return runCatching {
        BigDecimal(cleaned).toLong()
    }.getOrNull()
}

private fun readZipEntry(
    zip: ZipFile,
    path: String
): ByteArray {

    val entry =
        zip.getEntry(path)
            ?: throw IllegalStateException(
                "No se encontró $path dentro del Excel."
            )

    return zip.getInputStream(entry).use {
        it.readBytes()
    }
}

private fun textFromSi(
    parser: XmlPullParser
): String {

    val siDepth = parser.depth
    val result = StringBuilder()

    var event = parser.next()

    while (
        !(event == XmlPullParser.END_TAG &&
                parser.depth == siDepth &&
                parser.name == "si")
    ) {

        if (
            event == XmlPullParser.START_TAG &&
            parser.name == "t"
        ) {
            result.append(
                parser.nextText()
            )
        }

        event = parser.next()
    }

    return result.toString()
}

// -----------------------------------------------------------------------------
// EXCEL
// -----------------------------------------------------------------------------

object ExcelReader {

    private const val ORIGINAL_FILE =
        "excel_original.xlsx"

    private fun loadSharedStrings(
        bytes: ByteArray
    ): List<String> {

        val parser =
            Xml.newPullParser()

        parser.setInput(
            ByteArrayInputStream(bytes),
            "UTF-8"
        )

        val result =
            mutableListOf<String>()

        var event =
            parser.eventType

        while (
            event != XmlPullParser.END_DOCUMENT
        ) {

            if (
                event == XmlPullParser.START_TAG &&
                parser.name == "si"
            ) {

                result +=
                    textFromSi(parser)
            }

            event =
                parser.next()
        }

        return result
    }

    private fun column(
        ref: String
    ): String {

        return ref.takeWhile {
            it.isLetter()
        }
    }

    private fun readSheet(
        bytes: ByteArray,
        shared: List<String>
    ): List<MeterRecord> {

        val parser =
            Xml.newPullParser()

        parser.setInput(
            ByteArrayInputStream(bytes),
            "UTF-8"
        )

        val result =
            mutableListOf<MeterRecord>()

        var event =
            parser.eventType

        while (
            event != XmlPullParser.END_DOCUMENT
        ) {

            if (
                event == XmlPullParser.START_TAG &&
                parser.name == "row"
            ) {

                val rowNumber =
                    parser
                        .getAttributeValue(
                            null,
                            "r"
                        )
                        ?.toIntOrNull()
                        ?: 0

                val rowDepth =
                    parser.depth

                val cells =
                    mutableMapOf<String, String>()

                event =
                    parser.next()

                while (
                    !(event == XmlPullParser.END_TAG &&
                            parser.depth == rowDepth &&
                            parser.name == "row")
                ) {

                    if (
                        event == XmlPullParser.START_TAG &&
                        parser.name == "c"
                    ) {

                        val ref =
                            parser.getAttributeValue(
                                null,
                                "r"
                            ) ?: ""

                        val type =
                            parser.getAttributeValue(
                                null,
                                "t"
                            )

                        val cellDepth =
                            parser.depth

                        var value = ""

                        var inner =
                            parser.next()

                        while (
                            !(inner == XmlPullParser.END_TAG &&
                                    parser.depth == cellDepth &&
                                    parser.name == "c")
                        ) {

                            if (
                                inner == XmlPullParser.START_TAG &&
                                parser.name == "v"
                            ) {

                                value =
                                    parser.nextText()
                            }

                            inner =
                                parser.next()
                        }

                        if (
                            type == "s" &&
                            value.isNotBlank()
                        ) {

                            val index =
                                value.toIntOrNull()

                            if (index != null) {

                                value =
                                    shared.getOrNull(
                                        index
                                    ) ?: value
                            }
                        }

                        cells[
                            column(ref)
                        ] = value
                    }

                    event =
                        parser.next()
                }

                /*
                 * Cada fila con medidor es un registro independiente.
                 *
                 * rowNumber identifica la fila original del Excel.
                 * Por eso dos medidores iguales NO se fusionan.
                 */
                if (
                    rowNumber > 1 &&
                    cells["C"]
                        .orEmpty()
                        .isNotBlank()
                ) {

                    result +=
                        MeterRecord(

                            rowNumber =
                                rowNumber,

                            nir =
                                cells["A"]
                                    .orEmpty(),

                            address =
                                cells["B"]
                                    .orEmpty(),

                            meter =
                                normalizeMeter(
                                    cells["C"]
                                        .orEmpty()
                                ),

                            previousReading =
                                parseLongValue(
                                    cells["F"]
                                        .orEmpty()
                                ),

                            neighborhood =
                                cells["G"]
                                    .orEmpty(),

                            user =
                                cells["H"]
                                    .orEmpty(),

                            observation =
                                cells["I"]
                                    .orEmpty(),

                            currentReading =
                                parseLongValue(
                                    cells["D"]
                                        .orEmpty()
                                )
                        )
                }
            }

            event =
                parser.next()
        }

        return result
    }

    fun parse(
        context: Context,
        uri: Uri
    ): List<MeterRecord> {

        val temp =
            File.createTempFile(
                "lecturas_import_",
                ".xlsx",
                context.cacheDir
            )

        try {

            context.contentResolver
                .openInputStream(uri)
                .use { input ->

                    requireNotNull(input) {
                        "No se pudo abrir el archivo Excel."
                    }

                    temp.outputStream().use {
                            output ->
                        input.copyTo(output)
                    }
                }

            ZipFile(temp).use { zip ->

                val sharedStrings =
                    if (
                        zip.getEntry(
                            "xl/sharedStrings.xml"
                        ) != null
                    ) {

                        loadSharedStrings(
                            readZipEntry(
                                zip,
                                "xl/sharedStrings.xml"
                            )
                        )

                    } else {
                        emptyList()
                    }

                return readSheet(
                    readZipEntry(
                        zip,
                        "xl/worksheets/sheet1.xml"
                    ),
                    sharedStrings
                )
            }

        } finally {

            temp.delete()
        }
    }

    fun copyOriginal(
        context: Context,
        uri: Uri
    ) {

        context.contentResolver
            .openInputStream(uri)
            .use { input ->

                requireNotNull(input) {
                    "No se pudo abrir el Excel original."
                }

                File(
                    context.filesDir,
                    ORIGINAL_FILE
                ).outputStream().use {
                        output ->

                    input.copyTo(output)
                }
            }
    }

    fun export(
        context: Context,
        records: List<MeterRecord>,
        destination: Uri
    ) {

        val original =
            File(
                context.filesDir,
                ORIGINAL_FILE
            )

        require(original.exists()) {
            "Primero debes importar un Excel."
        }

        val updates =
            records
                .filter {
                    it.currentReading != null
                }
                .associate {
                    it.rowNumber to
                            it.currentReading!!
                }

        ZipFile(original).use { zip ->

            context.contentResolver
                .openOutputStream(destination)
                .use { output ->

                    requireNotNull(output) {
                        "No se pudo crear el archivo."
                    }

                    ZipOutputStream(
                        BufferedOutputStream(output)
                    ).use { zout ->

                        val entries =
                            zip.entries()

                        while (
                            entries.hasMoreElements()
                        ) {

                            val entry =
                                entries.nextElement()

                            val newEntry =
                                ZipEntry(
                                    entry.name
                                ).apply {
                                    time =
                                        entry.time
                                }

                            zout.putNextEntry(
                                newEntry
                            )

                            val data =
                                zip.getInputStream(
                                    entry
                                ).use {
                                    it.readBytes()
                                }

                            val outputData =
                                if (
                                    entry.name ==
                                    "xl/worksheets/sheet1.xml"
                                ) {

                                    updateSheetXml(
                                        data,
                                        updates
                                    )

                                } else {
                                    data
                                }

                            zout.write(
                                outputData
                            )

                            zout.closeEntry()
                        }
                    }
                }
        }
    }

    private fun updateSheetXml(
        bytes: ByteArray,
        updates: Map<Int, Long>
    ): ByteArray {

        /*
         * CORRECCIÓN IMPORTANTE:
         *
         * ByteArray.toString() espera un Charset.
         *
         * NO usamos .name(), porque eso convierte UTF_8
         * en String y provoca el error de compilación.
         */
        var xml =
            bytes.toString(
                StandardCharsets.UTF_8
            )

        for (
        (row, reading) in updates
        ) {

            val rowRegex =
                Regex(
                    "(<row\\b[^>]*\\br=\\\"$row\\\"[^>]*>)(.*?)(</row>)",
                    RegexOption.DOT_MATCHES_ALL
                )

            xml =
                rowRegex.replace(
                    xml
                ) { match ->

                    val body =
                        match.groupValues[2]

                    val cellRegex =
                        Regex(
                            "<c\\b[^>]*\\br=\\\"D$row\\\"[^>]*>.*?</c>",
                            RegexOption.DOT_MATCHES_ALL
                        )

                    val newCell =
                        "<c r=\"D$row\"><v>$reading</v></c>"

                    val newBody =
                        if (
                            cellRegex.containsMatchIn(
                                body
                            )
                        ) {

                            cellRegex.replace(
                                body
                            ) { oldCell ->

                                val openTag =
                                    Regex(
                                        "<c\\b[^>]*>"
                                    )
                                        .find(
                                            oldCell.value
                                        )
                                        ?.value
                                        ?: "<c r=\"D$row\">"

                                "${openTag}<v>$reading</v></c>"
                            }

                        } else {

                            val cCell =
                                Regex(
                                    "<c\\b[^>]*\\br=\"C$row\"[^>]*>.*?</c>",
                                    RegexOption.DOT_MATCHES_ALL
                                )
                                    .find(body)

                            if (cCell != null) {

                                val insertAt =
                                    cCell.range.last + 1

                                body.substring(
                                    0,
                                    insertAt
                                ) +
                                        newCell +
                                        body.substring(
                                            insertAt
                                        )

                            } else {

                                body +
                                        newCell
                            }
                        }

                    match.groupValues[1] +
                            newBody +
                            match.groupValues[3]
                }
        }

        return xml.toByteArray(
            StandardCharsets.UTF_8
        )
    }
}

// -----------------------------------------------------------------------------
// VIEWMODEL
// -----------------------------------------------------------------------------

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

    fun importExcel(
        uri: Uri
    ) {

        isBusy = true

        try {

            val app =
                getApplication<Application>()

            val parsed =
                ExcelReader.parse(
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

    val neighborhoodGroups:
            List<NeighborhoodGroup>
        get() {

            val query =
                search
                    .trim()
                    .lowercase()

            val source =
                if (
                    query.isBlank()
                ) {
                    records
                } else {
                    filtered
                }

            val grouped =
                LinkedHashMap<
                        String,
                        MutableList<MeterRecord>
                        >()

            source.forEach {
                    record ->

                val name =
                    record.neighborhood
                        .trim()
                        .ifBlank {
                            "SIN BARRIO"
                        }

                grouped
                    .getOrPut(name) {
                        mutableListOf()
                    }
                    .add(record)
            }

            return grouped.map {
                    (name, list) ->

                NeighborhoodGroup(
                    name = name,
                    records = list
                )
            }
        }

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
}

// -----------------------------------------------------------------------------
// MAIN ACTIVITY
// -----------------------------------------------------------------------------

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {
            App()
        }
    }
}

// -----------------------------------------------------------------------------
// INTERFAZ
// -----------------------------------------------------------------------------

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun App(
    vm: ReadingViewModel =
        viewModel()
) {

    var selected by
    remember {
        mutableStateOf<MeterRecord?>(
            null
        )
    }

    val expandedNeighborhoods =
        remember {
            mutableStateMapOf<
                    String,
                    Boolean
                    >()
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                vm.importExcel(uri)
            }
        }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        ) { uri ->

            if (uri != null) {
                vm.exportExcel(uri)
            }
        }

    LaunchedEffect(
        vm.message
    ) {

        if (vm.message != null) {

            kotlinx.coroutines.delay(
                2500
            )

            vm.clearMessage()
        }
    }

    MaterialTheme {

        Scaffold(

            topBar = {

                TopAppBar(
                    title = {
                        Text(
                            "Lecturas Gas Beta"
                        )
                    }
                )
            }

        ) { paddingValues ->

            Column(

                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            paddingValues
                        )
                        .padding(12.dp)

            ) {

                // -------------------------------------------------------------
                // BOTONES
                // -------------------------------------------------------------

                Button(

                    onClick = {

                        importLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/octet-stream"
                            )
                        )
                    },

                    enabled =
                        !vm.isBusy,

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text(
                        "Importar Excel"
                    )
                }

                Spacer(
                    Modifier.height(8.dp)
                )

                OutlinedButton(

                    onClick = {

                        exportLauncher.launch(
                            "Lecturas_A_terminado.xlsx"
                        )
                    },

                    enabled =
                        vm.imported &&
                                !vm.isBusy,

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text(
                        "Exportar"
                    )
                }

                Spacer(
                    Modifier.height(8.dp)
                )

                // -------------------------------------------------------------
                // RESUMEN
                // -------------------------------------------------------------

                if (vm.imported) {

                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Column(
                            Modifier.padding(12.dp)
                        ) {

                            Text(
                                "Ruta A",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            Text(
                                "${vm.records.size} registros  •  " +
                                        "✓ ${vm.readCount} leídos  •  " +
                                        "🔴 ${vm.pendingCount} pendientes"
                            )

                            Text(
                                "Barrios: ${vm.neighborhoodGroups.size}",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                } else {

                    Text(
                        "Importa el Excel de la ruta para comenzar."
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )
                }

                // -------------------------------------------------------------
                // BUSCADOR
                // -------------------------------------------------------------

                OutlinedTextField(

                    value =
                        vm.search,

                    onValueChange = {
                        vm.search = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    label = {

                        Text(
                            "Buscar medidor, últimos 4 dígitos, dirección o usuario"
                        )
                    },

                    singleLine = true
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                if (vm.imported) {

                    Text(

                        if (
                            vm.search.isBlank()
                        ) {
                            "Barrios de la ruta"
                        } else {
                            "Resultados agrupados por barrio"
                        },

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        Modifier.height(4.dp)
                    )
                }

                // -------------------------------------------------------------
                // LISTA
                // -------------------------------------------------------------

                LazyColumn(

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        ),

                    modifier =
                        Modifier.fillMaxSize()

                ) {

                    vm.neighborhoodGroups
                        .forEach { group ->

                            val groupKey =
                                group.name

                            val expanded =
                                expandedNeighborhoods[
                                    groupKey
                                ] ?: false

                            item(
                                key =
                                    "header:$groupKey"
                            ) {

                                Card(

                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {

                                                expandedNeighborhoods[
                                                    groupKey
                                                ] =
                                                    !expanded
                                            },

                                    colors =
                                        CardDefaults
                                            .cardColors(
                                                containerColor =
                                                    MaterialTheme
                                                        .colorScheme
                                                        .primaryContainer
                                            )

                                ) {

                                    Row(

                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    12.dp
                                                ),

                                        horizontalArrangement =
                                            Arrangement.SpaceBetween

                                    ) {

                                        Column {

                                            Text(

                                                group.name,

                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .titleMedium
                                            )

                                            Text(

                                                "${group.total} medidores  •  " +
                                                        "✓ ${group.read} leídos  •  " +
                                                        "🔴 ${group.pending} pendientes",

                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .bodySmall
                                            )
                                        }

                                        Text(
                                            if (
                                                expanded
                                            ) {
                                                "▲"
                                            } else {
                                                "▼"
                                            }
                                        )
                                    }
                                }
                            }

                            if (expanded) {

                                group.records
                                    .forEach {
                                            record ->

                                        item(
                                            key =
                                                "record:${record.rowNumber}"
                                        ) {

                                            val duplicate =
                                                vm.duplicateMeters
                                                    .contains(
                                                        record.meter
                                                    )

                                            Card(

                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selected =
                                                                record
                                                        }

                                            ) {

                                                Column(
                                                    Modifier.padding(
                                                        12.dp
                                                    )
                                                ) {

                                                    Text(

                                                        record.meter,

                                                        style =
                                                            MaterialTheme
                                                                .typography
                                                                .titleMedium
                                                    )

                                                    Text(
                                                        record.user
                                                    )

                                                    Text(
                                                        record.address
                                                    )

                                                    Spacer(
                                                        Modifier.height(
                                                            4.dp
                                                        )
                                                    )

                                                    Text(

                                                        if (
                                                            record.currentReading ==
                                                            null
                                                        ) {
                                                            "🔴 Pendiente  •  Anterior: " +
                                                                    (
                                                                            record.previousReading
                                                                                ?: "—"
                                                                            )
                                                        } else {
                                                            "✓ Lectura ya leída: " +
                                                                    record.currentReading
                                                        }
                                                    )

                                                    if (
                                                        duplicate
                                                    ) {

                                                        Spacer(
                                                            Modifier.height(
                                                                4.dp
                                                            )
                                                        )

                                                        Text(

                                                            "⚠ Medidor repetido: " +
                                                                    "este registro es independiente.",

                                                            style =
                                                                MaterialTheme
                                                                    .typography
                                                                    .labelSmall
                                                        )
                                                    }

                                                    if (
                                                        record.observation
                                                            .isNotBlank()
                                                    ) {

                                                        Spacer(
                                                            Modifier.height(
                                                                4.dp
                                                            )
                                                        )

                                                        Text(

                                                            "Observación: " +
                                                                    record.observation
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                }
            }
        }

        // ---------------------------------------------------------------------
        // DIÁLOGO DE LECTURA
        // ---------------------------------------------------------------------

        selected?.let {
                record ->

            var reading by
            remember(
                record.rowNumber
            ) {
                mutableStateOf("")
            }

            val alreadyRead =
                record.currentReading != null

            AlertDialog(

                onDismissRequest = {
                    selected = null
                },

                title = {
                    Text(
                        record.meter
                    )
                },

                text = {

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {

                        Text(
                            "Usuario: ${record.user}"
                        )

                        Text(
                            "Dirección: ${record.address}"
                        )

                        Text(
                            "Barrio: ${record.neighborhood}"
                        )

                        Text(
                            "Lectura anterior: " +
                                    (
                                            record.previousReading
                                                ?: "—"
                                            )
                        )

                        if (
                            record.observation
                                .isNotBlank()
                        ) {

                            Text(
                                "Observación: " +
                                        record.observation
                            )
                        }

                        if (
                            alreadyRead
                        ) {

                            Text(

                                "✓ LECTURA YA LEÍDA",

                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            Text(
                                "Lectura registrada: " +
                                        record.currentReading
                            )

                        } else {

                            OutlinedTextField(

                                value =
                                    reading,

                                onValueChange = {

                                    reading =
                                        it.filter(
                                            Char::isDigit
                                        )
                                },

                                label = {

                                    Text(
                                        "Lectura actual"
                                    )
                                },

                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType =
                                            KeyboardType.Number
                                    ),

                                singleLine = true
                            )
                        }
                    }
                },

                confirmButton = {

                    if (!alreadyRead) {

                        Button(

                            enabled =
                                reading.isNotBlank(),

                            onClick = {

                                if (
                                    vm.saveReading(
                                        record.rowNumber,
                                        reading
                                    )
                                ) {

                                    selected =
                                        null
                                }
                            }

                        ) {

                            Text(
                                "Guardar lectura"
                            )
                        }
                    }
                },

                dismissButton = {

                    TextButton(

                        onClick = {
                            selected = null
                        }

                    ) {

                        Text(
                            "Cerrar"
                        )
                    }
                }
            )
        }

        // ---------------------------------------------------------------------
        // MENSAJE
        // ---------------------------------------------------------------------

        vm.message?.let {
                msg ->

            Surface(

                modifier =
                    Modifier.padding(
                        16.dp
                    ),

                shadowElevation =
                    8.dp

            ) {

                Text(
                    msg,
                    Modifier.padding(
                        16.dp
                    )
                )
            }
        }
    }
}