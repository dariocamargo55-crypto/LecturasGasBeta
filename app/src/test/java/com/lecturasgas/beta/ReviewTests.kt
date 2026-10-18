package com.lecturasgas.beta

import com.lecturasgas.beta.review.ExportReview
import com.lecturasgas.beta.review.ExportReview.Kind
import com.lecturasgas.beta.review.MeterHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportReviewTest {

    /** 30 medidores con consumo normal (10) para tener una mediana, más los casos raros. */
    private fun base() = (1..30).map { rec(it, previous = 100, app = 110) }.toMutableList()

    @Test fun listaLoRaroYNadaMas() {
        val l = base()
        l[0] = rec(1, previous = 1210, app = 1021)                  // menor
        l[1] = rec(2, previous = 100, app = 900)                    // muy alto
        l[2] = rec(3, previous = 100, app = 100)                    // cero
        l[3] = rec(4, previous = 100, app = 120, excel = 115)       // cambia la del Excel
        l[4] = rec(5, previous = 100, app = null, skip = "Casa cerrada")
        l[5] = rec(6, previous = 100, app = null)                   // sin leer
        l[6] = rec(7, previous = 100, app = 110, realMeter = "ME-777")
        val r = ExportReview.analyze(l)
        assertEquals(listOf(1), r.section(Kind.NEGATIVE)!!.items.map { it.row })
        assertEquals(listOf(2), r.section(Kind.HIGH)!!.items.map { it.row })
        assertEquals(listOf(3), r.section(Kind.ZERO)!!.items.map { it.row })
        assertEquals(listOf(4), r.section(Kind.OVERWRITE)!!.items.map { it.row })
        assertEquals(listOf(5), r.section(Kind.SKIPPED)!!.items.map { it.row })
        assertEquals(listOf(6), r.section(Kind.UNREAD)!!.items.map { it.row })
        assertEquals(listOf(7), r.section(Kind.METER_FIX)!!.items.map { it.row })
        assertTrue(r.hasErrors); assertTrue(r.hasWarnings); assertFalse(r.clean)
        assertEquals(28, r.toWrite)
        assertEquals(10L, r.typicalConsumption)
    }

    @Test fun avisaCuandoFaltaUnDigito() {
        val r = ExportReview.analyze(base().also { it[0] = rec(1, previous = 1210, app = 121) })
        assertTrue(r.section(Kind.NEGATIVE)!!.items[0].detail.contains("faltó un dígito"))
    }

    @Test fun sinSuficientesDatosUsaUnLimiteFijo() {
        val r = ExportReview.analyze(listOf(rec(1, previous = 100, app = 5000), rec(2, previous = 100, app = 110), rec(3, previous = 100, app = 240)))
        assertNull(r.typicalConsumption)
        assertEquals(listOf(1), r.section(Kind.HIGH)!!.items.map { it.row })   // +4900 sí; +140 no (límite 150)
    }

    @Test fun todoNormalEstaLimpio() {
        val r = ExportReview.analyze(base())
        assertTrue(r.clean)
        assertEquals(30, r.toWrite)
    }

    @Test fun lasLecturasQueYaVeniaDelExcelNoSeMarcanSiNoCambian() {
        val l = base().also { it[0] = rec(1, previous = 100, app = 110, excel = 110) }
        assertNull(ExportReview.analyze(l).section(Kind.OVERWRITE))
    }
}

class MeterHistoryTest {

    @Test fun juntaMesesYCalculaPromedio() {
        val r = rec(1, previous = 150, app = 170)                    // este mes: consumo 20
        val snaps = listOf(
            MeterHistory.Snapshot("2025 agosto", 150, 130, 5L, true),   // 20
            MeterHistory.Snapshot("2025 julio", 130, 100, null, false)  // 30
        )
        val v = MeterHistory.build(r, "2025 septiembre", snaps, emptyList())
        assertEquals(3, v.entries.size)          // este mes + agosto + julio (anterior del Excel = agosto: no se repite)
        assertEquals(20L, v.entries[0].consumption)
        assertEquals(25L, v.average)
        assertEquals("−20 %", v.comparison)
    }

    @Test fun sinHistorialMuestraLaAnteriorDelExcel() {
        val v = MeterHistory.build(rec(1, previous = 150, app = null), "", emptyList(), emptyList())
        assertEquals(2, v.entries.size)
        assertEquals("Mes anterior (según el Excel)", v.entries[1].label)
        assertNull(v.average); assertNull(v.comparison)
    }
}

class ProgressTest {
    @org.junit.Test
    fun `comparar informa un avance que sube y termina en 100`() {
        val rows = (1..4000).map { TRow("N$it", "Calle $it", "M$it", null, "100") }
        val dir = java.nio.file.Files.createTempDirectory("prog").toFile()
        val a = java.io.File(dir, "a.xlsx"); val b = java.io.File(dir, "b.xlsx")
        Xlsx.build(a, rows)
        Xlsx.build(b, rows)
        val seen = mutableListOf<Float>()
        val res = com.lecturasgas.beta.data.excel.ExcelComparer.compare(a, b, "D") { _, f -> seen.add(f) }
        org.junit.Assert.assertTrue(res.ok)
        org.junit.Assert.assertTrue("pocos avisos: ${seen.size}", seen.size >= 8)
        org.junit.Assert.assertEquals(1f, seen.last(), 0.0001f)
        for (i in 1 until seen.size) org.junit.Assert.assertTrue("retrocede en $i: ${seen[i-1]} -> ${seen[i]}", seen[i] >= seen[i-1] - 0.0001f)
        org.junit.Assert.assertTrue(seen.all { it in 0f..1f })
    }

    @org.junit.Test
    fun `cancelar corta la comparacion y deja los archivos intactos`() {
        val rows = (1..4000).map { TRow("N$it", "Calle $it", "M$it", null, "100") }
        val dir = java.nio.file.Files.createTempDirectory("canc").toFile()
        val a = java.io.File(dir, "a.xlsx"); val b = java.io.File(dir, "b.xlsx")
        Xlsx.build(a, rows); Xlsx.build(b, rows)
        val before = a.readBytes().contentHashCode()
        var calls = 0
        try {
            com.lecturasgas.beta.data.excel.ExcelComparer.compare(a, b, "D") { _, f ->
                calls++
                if (f > 0.3f) throw com.lecturasgas.beta.data.excel.TaskCancelled()
            }
            org.junit.Assert.fail("debía cancelarse")
        } catch (e: com.lecturasgas.beta.data.excel.TaskCancelled) { /* esperado */ }
        org.junit.Assert.assertTrue(calls > 0)
        org.junit.Assert.assertEquals(before, a.readBytes().contentHashCode())
    }
}

class ReadingsReportTest {
    private fun sample(): List<com.lecturasgas.beta.data.model.MeterRecord> {
        val list = ArrayList<com.lecturasgas.beta.data.model.MeterRecord>()
        for (i in 2..45) list.add(rec(i, previous = 1000L + i, app = 1000L + i + 12))
        list.add(rec(46, previous = 5000, app = 4990))               // menor
        list.add(rec(47, previous = 200, app = 843278))              // muy alto
        list.add(rec(48, previous = 300, app = 300))                 // cero
        list.add(rec(49, previous = 100, excel = 90, app = 120))     // cambia lectura del Excel
        list.add(rec(50, previous = 100, realMeter = "ZZ-9", app = 113))
        list.add(rec(51, previous = 100))                            // sin leer
        list.add(rec(52, previous = 100, skip = "Casa cerrada"))
        return list
    }

    @org.junit.Test
    fun `el texto tiene encabezado, resumen, revision y lista en orden de fila`() {
        val text = com.lecturasgas.beta.review.ReadingsReport.build("Ruta A", "2026 septiembre", sample(), 1_700_000_000_000L)
        java.io.File("/tmp/claude-0/-home-claude/e3ec2f9f-8bd9-52b0-a18d-8dfeeda75300/scratchpad/reporte_demo.txt").writeText(text)
        org.junit.Assert.assertTrue(text.contains("RESPALDO DE LECTURAS"))
        org.junit.Assert.assertTrue(text.contains("Ruta: Ruta A"))
        org.junit.Assert.assertTrue(text.contains("RESUMEN"))
        org.junit.Assert.assertTrue(text.contains("PARA REVISAR"))
        org.junit.Assert.assertTrue(text.contains("▼ MENOR"))
        org.junit.Assert.assertTrue(text.contains("▲ ALTO"))
        org.junit.Assert.assertTrue(text.contains("= CERO"))
        org.junit.Assert.assertTrue(text.contains("Total: 49 lecturas"))
        // Las filas de la lista completa salen en orden creciente.
        val rows = Regex("(?m)^\\s*\\d+ │ \\s*(\\d+) │ ").findAll(text.substringAfter("LECTURAS TOMADAS")).map { it.groupValues[1].toInt() }.toList()
        org.junit.Assert.assertEquals(49, rows.size)
        org.junit.Assert.assertEquals(rows.sorted(), rows)
        // Cada línea de datos tiene el mismo ancho de columnas (alineado).
        val widths = text.lines().filter { Regex("^\\s*\\d+ │ \\s*\\d+ │ ").containsMatchIn(it) }.map { it.substring(0, it.lastIndexOf('│')).length }.toSet()
        org.junit.Assert.assertEquals(1, widths.size)
    }

    @org.junit.Test
    fun `sin lecturas el texto lo dice y no falla`() {
        val text = com.lecturasgas.beta.review.ReadingsReport.build("Ruta A", "", listOf(rec(2), rec(3)), 0L)
        org.junit.Assert.assertTrue(text.contains("todavía no hay lecturas"))
    }
}

class HistoryStatsTest {
    private fun s(id: Long, start: Long, end: Long?, meters: Double, n: Int, route: String = "Ruta A") =
        com.lecturasgas.beta.data.model.SessionSummary(
            com.lecturasgas.beta.data.model.DeliverySession(id, 1L, start, end, meters), n, route
        )

    @org.junit.Test
    fun `totales, dias y ritmo`() {
        val h = 3_600_000L
        val d1 = java.util.GregorianCalendar(2026, 9, 17, 8, 0).timeInMillis
        val d2 = java.util.GregorianCalendar(2026, 9, 18, 8, 0).timeInMillis
        val list = listOf(
            s(3, d2, d2 + 2 * h, 1500.0, 20),
            s(2, d1 + 5 * h, d1 + 6 * h, 500.0, 10),    // mismo día que la siguiente
            s(1, d1, d1 + 3 * h, 3000.0, 30),
            s(4, d2 + 9 * h, null, 0.0, 4)               // en curso: no suma tiempo
        )
        val t = com.lecturasgas.beta.review.HistoryStats.totals(list)
        org.junit.Assert.assertEquals(4, t.sessions)
        org.junit.Assert.assertEquals(64, t.deliveries)
        org.junit.Assert.assertEquals(5000.0, t.meters, 0.001)
        org.junit.Assert.assertEquals(6 * h, t.millis)
        org.junit.Assert.assertEquals(2, t.days)
        org.junit.Assert.assertEquals(10.0, com.lecturasgas.beta.review.HistoryStats.perHour(20, 2 * h)!!, 0.001)
        org.junit.Assert.assertNull(com.lecturasgas.beta.review.HistoryStats.perHour(3, 60_000L))
    }

    @org.junit.Test
    fun `agrupa por mes en el orden recibido`() {
        val oct = java.util.GregorianCalendar(2026, 9, 17).timeInMillis
        val sep = java.util.GregorianCalendar(2026, 8, 20).timeInMillis
        val months = com.lecturasgas.beta.review.HistoryStats.byMonth(listOf(s(3, oct, oct + 1, 0.0, 1), s(2, oct - 1000, null, 0.0, 1), s(1, sep, sep + 1, 0.0, 1)))
        org.junit.Assert.assertEquals(listOf("Octubre 2026", "Septiembre 2026"), months.map { it.first })
        org.junit.Assert.assertEquals(listOf(2, 1), months.map { it.second.size })
    }
}

class ReadingOrderTest {
    @org.junit.Test
    fun `numera por el momento en que se tomo por primera vez`() {
        // Fila 7 se leyó primero, luego la 3, luego la 9; la 3 se corrigió después (readAt cambia, first no).
        val recs = listOf(
            rec(3, app = 110, first = 2000L).copy(readAt = 9000L),
            rec(7, app = 120, first = 1000L).copy(readAt = 1000L),
            rec(9, app = 130, first = 3000L).copy(readAt = 3000L),
            rec(11)                                           // sin leer: no entra
        )
        val e = com.lecturasgas.beta.review.ReadingOrder.build(recs)
        org.junit.Assert.assertEquals(listOf(7, 3, 9), e.map { it.record.row })
        org.junit.Assert.assertEquals(listOf(1, 2, 3), e.map { it.seq })
        org.junit.Assert.assertEquals(mapOf(7 to 1, 3 to 2, 9 to 3), com.lecturasgas.beta.review.ReadingOrder.sequence(recs))
    }

    @org.junit.Test
    fun `sin hora conocida cae al final y empata por fila`() {
        val recs = listOf(rec(5, app = 1).copy(readAt = null), rec(2, app = 1, first = 50L), rec(4, app = 1, first = 50L))
        org.junit.Assert.assertEquals(listOf(2, 4, 5), com.lecturasgas.beta.review.ReadingOrder.build(recs).map { it.record.row })
    }

    @org.junit.Test
    fun `agrupa por dia`() {
        val d1 = java.util.GregorianCalendar(2026, 9, 17, 8, 0).timeInMillis
        val d2 = java.util.GregorianCalendar(2026, 9, 18, 8, 0).timeInMillis
        val recs = listOf(rec(2, app = 1, first = d1), rec(3, app = 1, first = d1 + 60_000), rec(4, app = 1, first = d2))
        val g = com.lecturasgas.beta.review.ReadingOrder.byDay(com.lecturasgas.beta.review.ReadingOrder.build(recs))
        org.junit.Assert.assertEquals(listOf(2, 1), g.map { it.second.size })
    }

    @org.junit.Test
    fun `documento de texto trae el orden y los datos`() {
        val d1 = java.util.GregorianCalendar(2026, 9, 17, 8, 0).timeInMillis
        val recs = listOf(rec(5, app = 321, first = d1 + 60_000), rec(2, app = 100, first = d1), rec(8))
        val t = com.lecturasgas.beta.review.ReadingOrder.toText("RUTA A", "2026 oct", recs, d1)
        org.junit.Assert.assertTrue(t.contains("ORDEN DE LECTURA"))
        org.junit.Assert.assertTrue(t.contains("2 lecturas en 1 día"))
        org.junit.Assert.assertTrue(t.contains("faltan 1 por leer"))
        val lines = t.split("\r\n").filter { it.contains(" │ ") && it.trim().first().isDigit() }
        org.junit.Assert.assertEquals(2, lines.size)
        org.junit.Assert.assertTrue(lines[0].trim().startsWith("1") && lines[0].contains("100"))
        org.junit.Assert.assertTrue(lines[1].trim().startsWith("2") && lines[1].contains("321"))
    }
}
