package com.lecturasgas.beta

import com.lecturasgas.beta.data.excel.ExcelComparer
import com.lecturasgas.beta.data.excel.ExcelExporter
import com.lecturasgas.beta.data.excel.ExportVerifier
import com.lecturasgas.beta.data.excel.ImportValidator
import com.lecturasgas.beta.data.excel.SheetScanner
import com.lecturasgas.beta.data.excel.XlsxPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * Pruebas de punta a punta con un .xlsx de verdad (se fabrica en el momento): importar → escribir lecturas →
 * comparar con el original → verificar fila por fila. Son la red de seguridad de la regla de oro:
 * el Excel sale IGUAL, salvo la columna D de las filas leídas, y cada lectura queda en SU fila.
 */
class ExcelPipelineTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun rows(n: Int = 40) = (1..n).map { i ->
        TRow(nir = "${1000 + i}", address = "Cra $i # 4-${i}0", meter = "ME-${5000 + i}",
            reading = if (i % 7 == 0) "${200 + i}" else null, previous = "${150 + i}",
            barrio = if (i < 20) "Centro" else "El Prado", user = "Usuario $i", obs = if (i % 9 == 0) "Perro" else "")
    }

    private fun original(rows: List<TRow> = rows()): File = File(tmp.root, "original.xlsx").also { Xlsx.build(it, rows) }

    private fun export(original: File, updates: Map<Int, Long>): File {
        val out = File(tmp.root, "salida.xlsx")
        out.outputStream().use { ExcelExporter.export(original, it, updates, "D") }
        return out
    }

    @Test fun exportarYCompararConElOriginal() {
        val o = original()
        val updates = mapOf(2 to 160L, 3 to 161L, 10 to 175L, 41 to 999L, 9 to 209L) // 9 = fila 9 (i=8)
        val out = export(o, updates)
        val cmp = ExcelComparer.compare(o, out, "D")
        assertTrue(cmp.problems.toString(), cmp.ok)
        assertEquals(listOf(2, 3, 9, 10, 41), cmp.changedRows)
    }

    @Test fun cadaLecturaCaeEnLaFilaDeSuMedidor() {
        val o = original()
        val parsedOriginal = Xlsx.parse(o)
        val records = parsedOriginal.rows.map { rec(it.row, it.meter, it.nir, it.previous, it.reading) }
        val updates = mapOf(2 to 160L, 3 to 161L, 10 to 175L, 41 to 999L)
        val withApp = records.map { r -> updates[r.row]?.let { r.copy(appReading = it) } ?: r }
        val out = export(o, updates)
        val result = ExportVerifier.verify(Xlsx.parse(out), withApp, updates)
        assertTrue(result.problems.toString(), result.ok)
        assertEquals(4, result.readingsChecked)
        // las demás hojas y archivos internos quedan byte a byte
        ZipFile(o).use { a -> ZipFile(out).use { b ->
            for (name in listOf("xl/sharedStrings.xml", "xl/styles.xml", "xl/worksheets/sheet4.xml", "xl/workbook.xml")) {
                assertTrue(name, a.getInputStream(a.getEntry(name)).readBytes().contentEquals(b.getInputStream(b.getEntry(name)).readBytes()))
            }
        } }
    }

    @Test fun elVerificadorDetectaUnaLecturaEnLaFilaEquivocada() {
        val o = original()
        val parsed = Xlsx.parse(o)
        val records = parsed.rows.map { rec(it.row, it.meter, it.nir, it.previous, it.reading) }
        // La app cree que la lectura 500 es de la fila 5, pero el archivo la tiene en la 6.
        val wrong = export(o, mapOf(6 to 500L))
        val withApp = records.map { if (it.row == 5) it.copy(appReading = 500L) else it }
        val r = ExportVerifier.verify(Xlsx.parse(wrong), withApp, mapOf(5 to 500L))
        assertFalse(r.ok)
        assertTrue(r.problems.any { it.contains("Fila 5") })
        assertTrue(r.problems.any { it.contains("Fila 6") && it.contains("no tenía lectura nueva") })
    }

    @Test fun elVerificadorDetectaMedidorOCambiado() {
        val o = original()
        val parsed = Xlsx.parse(o)
        val records = parsed.rows.map { rec(it.row, it.meter, it.nir, it.previous, it.reading) }.toMutableList()
        records[3] = records[3].copy(meter = "OTRO-99")
        val out = export(o, emptyMap())
        val r = ExportVerifier.verify(Xlsx.parse(out), records, emptyMap())
        assertTrue(r.problems.any { it.contains("el medidor es") })
    }

    @Test fun reexportarEsIdempotente() {
        val o = original()
        val updates = mapOf(2 to 160L, 3 to 161L)
        val first = export(o, updates)
        val second = File(tmp.root, "segunda.xlsx")
        second.outputStream().use { ExcelExporter.export(first, it, updates, "D") }
        val a = ZipFile(first).use { z -> XlsxPaths.readText(z, XlsxPaths.firstSheetPath(z)) }
        val b = ZipFile(second).use { z -> XlsxPaths.readText(z, XlsxPaths.firstSheetPath(z)) }
        assertEquals(a, b)
    }

    @Test fun lasFormulasNoSeSobrescriben() {
        val data = rows(10).toMutableList()
        data[3] = data[3].copy(reading = "50", readingKind = "f")  // fila 5
        val o = original(data)
        val result = ExcelExporter.export(o, File(tmp.root, "x.xlsx").outputStream(), mapOf(5 to 70L, 6 to 71L), "D")
        assertEquals(listOf(5), result.skippedFormula)
        assertEquals(1, result.written)
        val out = File(tmp.root, "x.xlsx")
        assertTrue(ExcelComparer.compare(o, out, "D").ok)
    }

    @Test fun elComparadorDetectaUnCambioFueraDeLaColumnaD() {
        val o = original()
        val out = export(o, mapOf(2 to 160L))
        // Se estropea a propósito el NIR de la fila 2 en la copia.
        val broken = File(tmp.root, "roto.xlsx")
        ZipFile(out).use { z ->
            java.util.zip.ZipOutputStream(broken.outputStream()).use { zo ->
                for (e in z.entries()) {
                    zo.putNextEntry(java.util.zip.ZipEntry(e.name))
                    var bytes = z.getInputStream(e).readBytes()
                    if (e.name.endsWith("sheet3.xml")) bytes = String(bytes).replace("<c r=\"A2\" s=\"2\"><v>1001</v>", "<c r=\"A2\" s=\"2\"><v>1002</v>").toByteArray()
                    zo.write(bytes); zo.closeEntry()
                }
            }
        }
        val cmp = ExcelComparer.compare(o, broken, "D")
        assertFalse(cmp.ok)
        assertTrue(cmp.problems.first().contains("A2"))
    }

    // ---------- Validación al importar ----------

    private fun validate(file: File, sheetCount: Int = 2): ImportValidator.Report {
        val parsed = Xlsx.parse(file)
        val xml = ZipFile(file).use { z -> XlsxPaths.readText(z, XlsxPaths.firstSheetPath(z)) }
        return ImportValidator.validate(parsed, xml, sheetCount)
    }

    @Test fun unExcelNormalPasaLaPruebaDeExportacion() {
        val r = validate(original())
        assertTrue(r.selfTest.message, r.selfTest.ok)
        assertFalse(r.hasErrors)
        assertTrue(r.infos.any { it.title.contains("2 hojas") })
        assertTrue(r.infos.any { it.title.contains("ya traen lectura") })
    }

    @Test fun avisaMedidoresYNirRepetidos() {
        val data = rows(12).toMutableList()
        data[4] = data[4].copy(meter = data[1].meter, nir = data[1].nir)
        val r = validate(original(data))
        assertTrue(r.warnings.any { it.title.contains("medidor están repetidos") })
        assertTrue(r.warnings.any { it.title.contains("NIR están repetidos") })
    }

    @Test fun avisaFormulasYTextoEnLaColumnaDeLectura() {
        val data = rows(12).toMutableList()
        data[2] = data[2].copy(reading = "10", readingKind = "f")
        data[5] = data[5].copy(reading = "300", readingKind = "s")
        val r = validate(original(data))
        assertTrue(r.warnings.any { it.title.contains("fórmula") })
        assertTrue(r.infos.any { it.title.contains("guardadas como texto") })
        assertTrue(r.selfTest.ok)   // las fórmulas se saltan, no rompen la prueba
    }

    @Test fun avisaDecimalesYLecturasMenoresALaAnterior() {
        val data = rows(12).toMutableList()
        data[1] = data[1].copy(reading = "1146.5")
        data[3] = data[3].copy(reading = "20", previous = "90")
        val r = validate(original(data))
        assertTrue(r.warnings.any { it.title.contains("decimales") })
        assertTrue(r.warnings.any { it.title.contains("menores a la anterior") })
    }

    @Test fun avisaFilasConDatosSinNumeroDeMedidor() {
        val data = rows(12).toMutableList()
        data[6] = data[6].copy(meter = "")
        val r = validate(original(data))
        // la fila sin medidor no se carga: se avisa
        assertTrue(r.warnings.any { it.title.contains("no número de medidor") || it.title.contains("tienen datos pero no número de medidor") })
    }

    @Test fun laPruebaDeExportacionFallaConUnFormatoNoSoportado() {
        val parsed = Xlsx.parse(original())
        val xml = ZipFile(original()).use { z -> XlsxPaths.readText(z, XlsxPaths.firstSheetPath(z)) }
        val bad = xml.replace("<row ", "<x:row ").replace("</row>", "</x:row>").replace("<sheetData>", "<x:sheetData>")
        val r = ImportValidator.validate(parsed, bad)
        assertTrue(r.hasErrors)
    }

    @Test fun elScannerCuentaLoQueDebe() {
        val xml = """<worksheet><sheetData><row r="1"><c r="D1" t="s"><v>0</v></c></row>""" +
                """<row><c r="A2"><v>1</v></c><c r="D2"><v>1.5</v></c></row>""" +
                """<row r="3"><c><v>1</v></c><c r="D3"><f>A1</f><v>2</v></c></row></sheetData>""" +
                """<mergeCells count="1"><mergeCell ref="D5:D6"/></mergeCells></worksheet>"""
        val s = SheetScanner.scan(xml, "D")
        assertEquals(1, s.rowsWithoutNumber)
        assertEquals(1, s.cellsWithoutRef)
        assertEquals(listOf(2), s.decimalRows)
        assertEquals(listOf(3), s.formulaRows)
        assertEquals(1, s.mergedOnReading)
        assertEquals(setOf(1, 2, 3), s.rowsWithContent)
    }

    @Test fun filasSinNumeroSeDeducenPorOrden() {
        val xml = """<worksheet><sheetData><row r="1"><c r="A1"><v>1</v></c></row><row><c r="A2"><v>1</v></c></row></sheetData></worksheet>"""
        val (out, r) = ExcelExporter.patchSheet(xml, mapOf(2 to 55L), "D")
        assertEquals(1, r.written)
        assertTrue(out.contains("""<c r="D2"><v>55</v></c>"""))
    }

    @Test fun unaFilaConCeldasSinReferenciaNoSeToca() {
        val xml = """<worksheet><sheetData><row r="2"><c><v>1</v></c><c><v>2</v></c></row></sheetData></worksheet>"""
        val (out, r) = ExcelExporter.patchSheet(xml, mapOf(2 to 55L), "D")
        assertEquals(listOf(2), r.skippedFormula)
        assertEquals(xml, out)
    }

    @Test fun elExcelSinHeadersUsaLaDAunqueSeAvise() {
        val parsed = Xlsx.parse(original()).let {
            com.lecturasgas.beta.data.excel.ExcelImporter.Parsed(it.rows, it.columns, it.period, it.sheetPath,
                listOf("No se encontró 'lectura_actual'; se usa la columna D."))
        }
        val xml = ZipFile(original()).use { z -> XlsxPaths.readText(z, XlsxPaths.firstSheetPath(z)) }
        val r = ImportValidator.validate(parsed, xml)
        assertTrue(r.hasErrors)
        assertNull(r.errors.firstOrNull { !it.title.contains("encabezado") })
    }

    @Test fun unaRutaGrandeSeProcesaBienYRapido() {
        val big = original(rows(3000))
        val t0 = System.currentTimeMillis()
        val r = validate(big)
        val ms = System.currentTimeMillis() - t0
        assertTrue(r.selfTest.message, r.selfTest.ok)
        assertTrue("la validación tardó $ms ms", ms < 5000)
        val updates = (2..3001 step 3).associateWith { 300L + it }
        val out = export(big, updates)
        assertTrue(ExcelComparer.compare(big, out, "D").ok)
        val records = Xlsx.parse(big).rows.map { x -> rec(x.row, x.meter, x.nir, x.previous, x.reading).let { u -> updates[u.row]?.let { v -> u.copy(appReading = v) } ?: u } }
        assertTrue(ExportVerifier.verify(Xlsx.parse(out), records, updates).ok)
    }
}
