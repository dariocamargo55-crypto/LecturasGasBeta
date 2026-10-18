package com.lecturasgas.beta

import com.lecturasgas.beta.data.excel.ExcelExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del exportador (se ejecutan en el PC con el botón ▶ de Android Studio).
 * Comprueban la regla de oro: solo cambia la columna D.
 */
class ExcelExporterTest {

    private val sheet = """<worksheet><cols><col min="4" max="4" style="4"/></cols><sheetData>""" +
            """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="D1" t="s"><v>3</v></c></row>""" +
            """<row r="2"><c r="A2"><v>1</v></c><c r="C2" t="s"><v>10</v></c><c r="D2" s="17"><v>1146.0</v></c><c r="F2"><v>1145</v></c></row>""" +
            """<row r="3"><c r="A3"><v>2</v></c><c r="C3" t="s"><v>11</v></c><c r="D3" s="24"/><c r="F3"><v>900</v></c></row>""" +
            """<row r="4"><c r="A4"><v>3</v></c><c r="C4" t="s"><v>12</v></c><c r="F4"><v>50</v></c></row>""" +
            """<row r="5"><c r="A5"><v>4</v></c><c r="D5"><f>F5+1</f><v>7</v></c></row>""" +
            """</sheetData><sheetProtection sheet="1"/></worksheet>"""

    @Test
    fun soloCambiaLaColumnaD() {
        val (xml, r) = ExcelExporter.patchSheet(sheet, mapOf(2 to 1146L, 3 to 905L, 4 to 60L, 5 to 9L), "D")
        assertEquals(2, r.written)          // filas 3 y 4
        assertEquals(1, r.unchanged)        // fila 2 ya tenía 1146
        assertEquals(listOf(5), r.skippedFormula)
        assertTrue(xml.contains("""<c r="D3" s="24"><v>905</v></c>"""))
        // D4 no existía: se crea antes de F4 con el estilo de la columna
        assertTrue(xml.contains("""<c r="C4" t="s"><v>12</v></c><c r="D4" s="4"><v>60</v></c><c r="F4">"""))
        val strip = Regex("""<c r="D\d+"[^>]*?(?:/>|>.*?</c>)""")
        assertEquals(sheet.replace(strip, ""), xml.replace(strip, ""))
    }

    @Test
    fun reexportarNoCambiaNada() {
        val updates = mapOf(3 to 905L, 4 to 60L)
        val (first, _) = ExcelExporter.patchSheet(sheet, updates, "D")
        val (second, r) = ExcelExporter.patchSheet(first, updates, "D")
        assertEquals(first, second)
        assertEquals(0, r.written)
    }
}
