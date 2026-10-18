package com.lecturasgas.beta.data.excel

import android.util.Xml
import com.lecturasgas.beta.util.TextUtil
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Lee el Excel de la ruta SIN cargar todo en memoria:
 * recorre el XML de la hoja con un "pull parser" (lee etiqueta por etiqueta).
 *
 * Las columnas se detectan por el ENCABEZADO de la fila 1 (nir, Dirección,
 * medidor, lectura_actual, Lectura Anterior, barrio, usuario, Observación).
 * Si algún encabezado no aparece se usa la columna de siempre (A, B, C, D, F, G, H, I).
 */
object ExcelImporter {

    data class Columns(
        val nir: String = "A",
        val address: String = "B",
        val meter: String = "C",
        val reading: String = "D",
        val previous: String = "F",
        val barrio: String = "G",
        val user: String = "H",
        val observation: String = "I"
    )

    data class Row(
        val row: Int,
        val nir: String,
        val address: String,
        val meter: String,
        val previous: Long?,
        val barrio: String,
        val user: String,
        val observation: String,
        val reading: Long?
    )

    data class Parsed(
        val rows: List<Row>,
        val columns: Columns,
        val period: String,
        val sheetPath: String,
        val warnings: List<String>
    )

    /** onProgress(filasLeídas, filasTotalesAprox) se llama cada cierto número de filas. */
    fun parse(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Parsed {
        ZipFile(file).use { zip ->
            val sheetPath = XlsxPaths.firstSheetPath(zip)
            val shared = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                zip.getInputStream(entry).use { readSharedStrings(BufferedInputStream(it)) }
            } ?: emptyList()
            val sheetEntry = zip.getEntry(sheetPath) ?: error("El Excel no tiene hojas.")
            return zip.getInputStream(sheetEntry).use {
                readSheet(BufferedInputStream(it, 64 * 1024), shared, sheetPath, onProgress)
            }
        }
    }

    // ------------------------------------------------------------------

    private fun readSharedStrings(input: InputStream): List<String> {
        val list = ArrayList<String>(4096)
        val p = Xml.newPullParser()
        p.setInput(input, "UTF-8")
        val sb = StringBuilder()
        var inSi = false
        var inPhonetic = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "rPh" -> inPhonetic = true
                    "t" -> if (inSi && !inPhonetic) sb.append(p.nextText())
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "si" -> { list.add(sb.toString()); inSi = false }
                    "rPh" -> inPhonetic = false
                }
            }
            ev = p.next()
        }
        return list
    }

    private fun readSheet(
        input: InputStream,
        shared: List<String>,
        sheetPath: String,
        onProgress: (Int, Int) -> Unit
    ): Parsed {
        val p = Xml.newPullParser()
        p.setInput(input, "UTF-8")

        var totalRows = 0
        var columns = Columns()
        var period = ""
        val warnings = mutableListOf<String>()
        val rows = ArrayList<Row>(2500)
        var headerDone = false
        var rowsSeen = 0

        val cells = HashMap<String, String>(24)
        var currentRow = 0
        var cellCol = ""
        var cellType: String? = null
        var cellValue: String? = null
        var inInline = false

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "dimension" -> {
                        val ref = p.getAttributeValue(null, "ref") ?: ""
                        totalRows = TextUtil.digits(ref.substringAfter(':', "")).toIntOrNull() ?: 0
                    }
                    "row" -> {
                        currentRow = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (currentRow + 1)
                        cells.clear()
                    }
                    "c" -> {
                        cellCol = XlsxPaths.columnLetters(p.getAttributeValue(null, "r") ?: "")
                        cellType = p.getAttributeValue(null, "t")
                        cellValue = null
                    }
                    "v" -> cellValue = p.nextText()
                    "is" -> inInline = true
                    "t" -> if (inInline) cellValue = (cellValue ?: "") + p.nextText()
                }

                XmlPullParser.END_TAG -> when (p.name) {
                    "is" -> inInline = false
                    "c" -> {
                        val raw = cellValue
                        if (raw != null && cellCol.isNotEmpty()) {
                            val text = if (cellType == "s") {
                                shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                            } else raw
                            cells[cellCol] = text
                        }
                    }
                    "row" -> {
                        rowsSeen++
                        if (!headerDone) {
                            headerDone = true
                            val detected = detectColumns(cells, warnings)
                            columns = detected.first
                            period = detected.second
                        } else {
                            val meter = TextUtil.cleanNumber(cells[columns.meter].orEmpty())
                            if (meter.isNotBlank()) {
                                rows.add(
                                    Row(
                                        row = currentRow,
                                        nir = TextUtil.cleanNumber(cells[columns.nir].orEmpty()),
                                        address = cells[columns.address].orEmpty().trim(),
                                        meter = meter,
                                        previous = TextUtil.parseWhole(cells[columns.previous].orEmpty()),
                                        barrio = cells[columns.barrio].orEmpty().trim(),
                                        user = cells[columns.user].orEmpty().trim(),
                                        observation = cells[columns.observation].orEmpty().trim(),
                                        reading = TextUtil.parseWhole(cells[columns.reading].orEmpty())
                                    )
                                )
                            }
                        }
                        if (rowsSeen % 250 == 0) onProgress(rowsSeen, totalRows)
                    }
                }
            }
            ev = p.next()
        }
        onProgress(rowsSeen, rowsSeen)
        return Parsed(rows, columns, period, sheetPath, warnings)
    }

    /** Busca cada columna por el texto del encabezado. */
    private fun detectColumns(
        header: Map<String, String>,
        warnings: MutableList<String>
    ): Pair<Columns, String> {
        val d = Columns()
        var nir: String? = null
        var address: String? = null
        var meter: String? = null
        var reading: String? = null
        var previous: String? = null
        var barrio: String? = null
        var user: String? = null
        var observation: String? = null
        var period = ""

        val periodRegex = Regex("^\\d{4}\\s*[a-z]+$")
        for ((col, value) in header.entries.sortedBy { XlsxPaths.columnNumber(it.key) }) {
            val h = TextUtil.normalize(value)
            when {
                h.isEmpty() -> Unit
                h == "nir" || h.startsWith("nir ") -> if (nir == null) nir = col
                h.contains("direcc") -> if (address == null) address = col
                h.contains("medidor") -> if (meter == null) meter = col
                h.contains("anterior") -> if (previous == null) previous = col
                h.contains("lectura") -> if (reading == null) reading = col
                h.contains("barrio") -> if (barrio == null) barrio = col
                h.contains("usuario") || h.contains("suscriptor") -> if (user == null) user = col
                h.contains("observ") -> if (observation == null) observation = col
                periodRegex.matches(h) -> if (period.isEmpty()) period = value.trim()
            }
        }
        if (meter == null) warnings.add("No se encontró el encabezado 'medidor'; se usa la columna ${d.meter}.")
        if (reading == null) warnings.add("No se encontró 'lectura_actual'; se usa la columna ${d.reading}.")

        return Pair(
            Columns(
                nir = nir ?: d.nir,
                address = address ?: d.address,
                meter = meter ?: d.meter,
                reading = reading ?: d.reading,
                previous = previous ?: d.previous,
                barrio = barrio ?: d.barrio,
                user = user ?: d.user,
                observation = observation ?: d.observation
            ),
            period
        )
    }

    /** "ruta A septiembre.xlsx" -> "RUTA A — SEPTIEMBRE" */
    fun routeNameFromFile(fileName: String): String {
        val clean = fileName.trim()
            .replace(Regex("\\.[^.]+$"), "")
            .replace(Regex("[_\\s]+"), " ")
            .trim()
            .uppercase()
        if (clean.isBlank()) return "RUTA"
        val match = Regex("^(?:LECTURAS\\s+)?(?:RUTA\\s+)?([A-Z0-9]{1,3})\\s+(.+)$").matchEntire(clean)
        return if (match != null) "RUTA ${match.groupValues[1]} — ${match.groupValues[2]}" else clean
    }

    /** "RUTA A — SEP" -> "RUTA A": la identidad de la ruta sin el mes. */
    fun routeKeyOf(name: String): String =
        name.substringBefore('—').trim().uppercase().ifBlank { "RUTA" }
}
