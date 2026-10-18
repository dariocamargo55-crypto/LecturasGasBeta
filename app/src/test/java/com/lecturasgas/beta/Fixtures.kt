package com.lecturasgas.beta

import com.lecturasgas.beta.data.excel.ExcelImporter
import com.lecturasgas.beta.data.model.MeterRecord
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/** Un medidor de prueba. */
fun rec(
    row: Int, meter: String = "M-$row", nir: String = "${4000 + row}",
    previous: Long? = 100, excel: Long? = null, app: Long? = null,
    realMeter: String? = null, skip: String? = null, isNew: Boolean = false, first: Long? = null
) = MeterRecord(
    row = row, nir = nir, nirNumber = nir.toLongOrNull(), address = "Calle $row", meter = meter,
    meterKey = meter.filter { it.isLetterOrDigit() }.uppercase(), previous = previous, barrio = "Centro",
    user = "Usuario $row", observation = "", excelReading = excel, appReading = app,
    readAt = if (app != null) 1000L else null, note = "", lat = null, lon = null, locationSource = null,
    realMeter = realMeter, skipReason = skip, isNew = isNew, firstReadAt = first
)

/** Fila de un Excel de prueba. */
data class TRow(
    val nir: String, val address: String, val meter: String, val reading: String?, val previous: String?,
    val barrio: String = "Centro", val user: String = "Pepe", val obs: String = "",
    /** "n" número, "f" fórmula, "s" texto compartido, null = celda vacía con estilo */
    val readingKind: String = "n"
)

/** Fabrica un .xlsx real (zip con sharedStrings, estilos, protección y una segunda hoja) para probar de punta a punta. */
object Xlsx {
    fun build(file: File, rows: List<TRow>, extraSheet: Boolean = true) {
        val strings = ArrayList<String>()
        fun si(t: String): Int { val i = strings.indexOf(t); if (i >= 0) return i; strings.add(t); return strings.size - 1 }
        val headers = listOf("nir", "Dirección", "medidor", "lectura_actual", "", "Lectura Anterior", "barrio", "usuario", "Observación")
        val cols = "ABCDEFGHI"
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:I${rows.size + 1}"/>""")
        sb.append("""<cols><col min="4" max="4" width="14" style="4" customWidth="1"/></cols><sheetData>""")
        sb.append("""<row r="1">""")
        headers.forEachIndexed { i, h -> if (h.isNotEmpty()) sb.append("""<c r="${cols[i]}1" t="s" s="1"><v>${si(h)}</v></c>""") }
        sb.append("</row>")
        rows.forEachIndexed { i, r ->
            val n = i + 2
            sb.append("""<row r="$n" spans="1:9">""")
            sb.append("""<c r="A$n" s="2"><v>${r.nir}</v></c>""")
            sb.append("""<c r="B$n" t="s"><v>${si(r.address)}</v></c>""")
            sb.append("""<c r="C$n" t="s"><v>${si(r.meter)}</v></c>""")
            when (r.readingKind) {
                "f" -> sb.append("""<c r="D$n" s="17"><f>F$n+1</f><v>${r.reading ?: 0}</v></c>""")
                "s" -> if (r.reading != null) sb.append("""<c r="D$n" t="s" s="17"><v>${si(r.reading)}</v></c>""")
                else -> if (r.reading != null) sb.append("""<c r="D$n" s="17"><v>${r.reading}</v></c>""") else if (i % 2 == 0) sb.append("""<c r="D$n" s="24"/>""")
            }
            if (r.previous != null) sb.append("""<c r="F$n"><v>${r.previous}</v></c>""")
            sb.append("""<c r="G$n" t="s"><v>${si(r.barrio)}</v></c>""")
            sb.append("""<c r="H$n" t="s"><v>${si(r.user)}</v></c>""")
            if (r.obs.isNotEmpty()) sb.append("""<c r="I$n" t="s"><v>${si(r.obs)}</v></c>""")
            sb.append("</row>")
        }
        sb.append("""</sheetData><sheetProtection sheet="1" objects="1"/><pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75"/></worksheet>""")
        val ss = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
        strings.forEach { ss.append("<si><t>").append(it.replace("&", "&amp;").replace("<", "&lt;")).append("</t></si>") }
        ss.append("</sst>")
        ZipOutputStream(FileOutputStream(file)).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name).apply { time = 0 }); z.write(text.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""")
            put("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Hoja1" sheetId="1" r:id="rId1"/>${if (extraSheet) """<sheet name="Otra" sheetId="2" r:id="rId2"/>""" else ""}</sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="worksheets/sheet3.xml"/><Relationship Id="rId2" Type="x" Target="worksheets/sheet4.xml"/></Relationships>""")
            put("xl/worksheets/sheet3.xml", sb.toString())
            put("xl/worksheets/sheet4.xml", """<worksheet><sheetData><row r="1"><c r="D1"><v>999</v></c></row></sheetData></worksheet>""")
            put("xl/sharedStrings.xml", ss.toString())
            put("xl/styles.xml", """<styleSheet/>""")
        }
    }

    /** Lector independiente (StAX de Java) con las mismas reglas del importador de la app. Sirve para contrastar. */
    fun parse(file: File): ExcelImporter.Parsed {
        ZipFile(file).use { zip ->
            val shared = ArrayList<String>()
            zip.getEntry("xl/sharedStrings.xml")?.let { e ->
                val r = XMLInputFactory.newInstance().createXMLStreamReader(zip.getInputStream(e))
                val cur = StringBuilder()
                while (r.hasNext()) {
                    when (r.next()) {
                        XMLStreamConstants.START_ELEMENT -> if (r.localName == "si") cur.setLength(0) else if (r.localName == "t") cur.append(r.elementText)
                        XMLStreamConstants.END_ELEMENT -> if (r.localName == "si") shared.add(cur.toString())
                    }
                }
            }
            val sheet = com.lecturasgas.beta.data.excel.XlsxPaths.firstSheetPath(zip)
            val r = XMLInputFactory.newInstance().createXMLStreamReader(zip.getInputStream(zip.getEntry(sheet)))
            val rows = ArrayList<ExcelImporter.Row>()
            val cells = HashMap<String, String>()
            var rowNum = 0; var type: String? = null; var col = ""; var value: String? = null; var header = true
            val colIdx = ExcelImporter.Columns()
            while (r.hasNext()) {
                when (r.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                        "row" -> { rowNum = r.getAttributeValue(null, "r")?.toInt() ?: (rowNum + 1); cells.clear() }
                        "c" -> { col = (r.getAttributeValue(null, "r") ?: "").takeWhile { it.isLetter() }; type = r.getAttributeValue(null, "t"); value = null }
                        "v" -> value = r.elementText
                    }
                    XMLStreamConstants.END_ELEMENT -> when (r.localName) {
                        "c" -> value?.let { v -> cells[col] = if (type == "s") shared[v.trim().toInt()] else v }
                        "row" -> if (header) header = false else {
                            val meter = com.lecturasgas.beta.util.TextUtil.cleanNumber(cells[colIdx.meter].orEmpty())
                            if (meter.isNotBlank()) rows.add(ExcelImporter.Row(
                                rowNum, com.lecturasgas.beta.util.TextUtil.cleanNumber(cells[colIdx.nir].orEmpty()), cells[colIdx.address].orEmpty(), meter,
                                com.lecturasgas.beta.util.TextUtil.parseWhole(cells[colIdx.previous].orEmpty()), cells[colIdx.barrio].orEmpty(),
                                cells[colIdx.user].orEmpty(), cells[colIdx.observation].orEmpty(),
                                com.lecturasgas.beta.util.TextUtil.parseWhole(cells[colIdx.reading].orEmpty())
                            ))
                        }
                    }
                }
            }
            return ExcelImporter.Parsed(rows, colIdx, "2025 septiembre", sheet, emptyList())
        }
    }
}
