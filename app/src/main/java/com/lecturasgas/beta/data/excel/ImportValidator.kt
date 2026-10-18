package com.lecturasgas.beta.data.excel

import com.lecturasgas.beta.util.TextUtil

/**
 * Revisa el Excel RECIÉN LEÍDO, antes de guardarlo, y le dice al usuario si algo podría salir mal:
 * encabezados que faltan, medidores repetidos, celdas de lectura con fórmulas, etc.
 *
 * Además hace una PRUEBA REAL DE EXPORTACIÓN: escribe unas lecturas de ejemplo en una copia de la hoja
 * (solo en memoria), la compara con la original y confirma que lo único que cambió fue la columna de
 * lectura, en la fila correcta. Si esa prueba falla, la app no puede garantizar el Excel.
 * Es código puro (sin Android): se prueba en la computadora.
 */
object ImportValidator {

    enum class Level { ERROR, WARNING, INFO }

    data class Issue(
        val level: Level,
        val title: String,
        val detail: String = "",
        /** Filas del Excel afectadas (las primeras; para mostrar ejemplos). */
        val rows: List<Int> = emptyList()
    )

    data class SelfTest(val ran: Boolean, val ok: Boolean, val message: String, val rowsTried: Int)

    data class Report(val issues: List<Issue>, val selfTest: SelfTest) {
        val errors: List<Issue> get() = issues.filter { it.level == Level.ERROR }
        val warnings: List<Issue> get() = issues.filter { it.level == Level.WARNING }
        val infos: List<Issue> get() = issues.filter { it.level == Level.INFO }
        val hasErrors: Boolean get() = errors.isNotEmpty()
        val clean: Boolean get() = errors.isEmpty() && warnings.isEmpty()
    }

    fun validate(parsed: ExcelImporter.Parsed, sheetXml: String?, sheetCount: Int = 1, onProgress: Progress = NoProgress): Report {
        val issues = mutableListOf<Issue>()
        val rows = parsed.rows
        val col = parsed.columns.reading.uppercase()

        // ---- Encabezados que no se encontraron (se usó la columna de siempre) ----
        for (w in parsed.warnings) {
            issues.add(
                Issue(
                    Level.ERROR,
                    "Falta un encabezado en la fila 1",
                    "$w Confirma que esa columna es la correcta antes de importar."
                )
            )
        }
        if (col != "D") {
            issues.add(
                Issue(
                    Level.WARNING,
                    "La lectura está en la columna $col, no en la D",
                    "La app escribirá las lecturas en la columna $col (la que tiene el encabezado de lectura). Si tu Excel de siempre las lleva en la D, revisa el archivo."
                )
            )
        }

        // ---- Estructura de la hoja ----
        onProgress("Revisando el formato de la hoja…", 0f)
        val scan = sheetXml?.let { SheetScanner.scan(it, col) }
        if (scan != null) {
            if (scan.prefixedTags) issues.add(
                Issue(Level.ERROR, "El archivo usa un formato interno poco común",
                    "Las etiquetas de la hoja traen prefijo (por ejemplo <x:row>). La app no puede leerlo ni escribirlo con seguridad. Ábrelo y guárdalo de nuevo con Excel.")
            )
            if (scan.rowsWithoutNumber > 0 || scan.cellsWithoutRef > 0) issues.add(
                Issue(Level.WARNING, "Hay filas o celdas sin número de posición",
                    "${scan.rowsWithoutNumber} filas y ${scan.cellsWithoutRef} celdas no traen su referencia (por ejemplo D5). La app las deduce por orden. Si ves lecturas en un lugar raro, guarda el archivo otra vez con Excel.")
            )
            if (scan.mergedOnReading > 0) issues.add(
                Issue(Level.WARNING, "Hay celdas combinadas en la columna de lectura",
                    "${scan.mergedOnReading} rangos combinados tocan la columna $col; escribir ahí puede verse raro en Excel.")
            )
            if (scan.formulaRows.isNotEmpty()) issues.add(
                Issue(Level.WARNING, "${scan.formulaRows.size} celdas de lectura tienen fórmula",
                    "La app NO las sobrescribe: esas lecturas no se exportarán aunque las tomes.", scan.formulaRows.take(MAX_ROWS))
            )
            if (scan.decimalRows.isNotEmpty()) issues.add(
                Issue(Level.WARNING, "${scan.decimalRows.size} lecturas del Excel tienen decimales",
                    "La app trabaja con números enteros y las muestra sin decimales. Si no las cambias, el Excel las conserva tal cual.", scan.decimalRows.take(MAX_ROWS))
            )
            if (scan.textRows.isNotEmpty()) issues.add(
                Issue(Level.INFO, "${scan.textRows.size} lecturas están guardadas como texto",
                    "Si registras otra lectura en esas filas, la app la escribe como número.", scan.textRows.take(MAX_ROWS))
            )
            // Filas con datos que la app no cargó (sin número de medidor): no se podrán leer ni tocar.
            if (scan.rowsWithContent.isNotEmpty()) {
                val header = scan.rowsWithContent.first()
                val loaded = rows.mapTo(HashSet()) { it.row }
                val skipped = scan.rowsWithContent.filter { it != header && it !in loaded }
                if (skipped.isNotEmpty()) issues.add(
                    Issue(Level.WARNING, "${skipped.size} filas tienen datos pero no número de medidor",
                        "No aparecerán en la app y el Excel no las modifica.", skipped.take(MAX_ROWS))
                )
            }
        }
        if (sheetCount > 1) issues.add(
            Issue(Level.INFO, "El libro tiene $sheetCount hojas", "La app usa solo la primera. Las demás se copian sin cambios.")
        )

        onProgress("Revisando los datos…", 0.12f)
        // ---- Contenido ----
        val byMeter = rows.groupBy { TextUtil.meterKey(it.meter) }.filter { it.value.size > 1 }
        if (byMeter.isNotEmpty()) issues.add(
            Issue(Level.WARNING, "${byMeter.size} números de medidor están repetidos",
                "Ejemplos: " + byMeter.entries.take(3).joinToString("; ") { (k, v) -> "$k (filas ${v.take(4).joinToString { it.row.toString() }})" } +
                        ". La búsqueda y el GPS se guardan por número de medidor, así que esas filas se confundirían entre sí.",
                byMeter.values.flatMap { list -> list.map { it.row } }.take(MAX_ROWS))
        )
        val byNir = rows.filter { it.nir.isNotBlank() }.groupBy { it.nir }.filter { it.value.size > 1 }
        if (byNir.isNotEmpty()) issues.add(
            Issue(Level.WARNING, "${byNir.size} NIR están repetidos",
                "Ejemplos: " + byNir.entries.take(3).joinToString("; ") { (k, v) -> "$k (filas ${v.take(4).joinToString { it.row.toString() }})" },
                byNir.values.flatMap { list -> list.map { it.row } }.take(MAX_ROWS))
        )
        val noNir = rows.filter { it.nir.isBlank() }
        if (noNir.isNotEmpty()) issues.add(
            Issue(Level.WARNING, "${noNir.size} medidores no tienen NIR", "Se podrán leer, pero no buscarlos por NIR.", noNir.map { it.row }.take(MAX_ROWS))
        )
        val negativeExisting = rows.filter { it.reading != null && it.previous != null && it.reading < it.previous }
        if (negativeExisting.isNotEmpty()) issues.add(
            Issue(Level.WARNING, "${negativeExisting.size} lecturas del Excel son menores a la anterior",
                "Ya venían así en el archivo.", negativeExisting.map { it.row }.take(MAX_ROWS))
        )
        val negativeValues = rows.filter { (it.reading ?: 0L) < 0L }
        if (negativeValues.isNotEmpty()) issues.add(
            Issue(Level.WARNING, "${negativeValues.size} lecturas negativas", "", negativeValues.map { it.row }.take(MAX_ROWS))
        )
        val noPrev = rows.count { it.previous == null }
        if (noPrev > 0) issues.add(
            Issue(Level.INFO, "$noPrev medidores sin lectura anterior", "No se les podrá calcular el consumo.")
        )
        val already = rows.count { it.reading != null }
        if (already > 0) issues.add(
            Issue(Level.INFO, "$already medidores ya traen lectura en el Excel",
                "La app las muestra como leídas; solo cambian si registras otra lectura.")
        )

        // ---- Prueba real de exportación ----
        val test = if (sheetXml == null) {
            SelfTest(false, false, "No se pudo leer la hoja para probar la exportación.", 0)
        } else runSelfTest(sheetXml, parsed, onProgress.slice(0.2f, 1f))
        if (test.ran && !test.ok) issues.add(
            Issue(Level.ERROR, "La prueba de exportación falló", test.message + " La app no puede garantizar que el Excel salga igual al original.")
        )
        if (!test.ran) issues.add(Issue(Level.WARNING, "No se pudo hacer la prueba de exportación", test.message))

        val ordered = issues.sortedBy { it.level.ordinal }
        return Report(ordered, test)
    }

    /** Escribe lecturas de ejemplo en una copia de la hoja y comprueba que solo cambie la columna de lectura. */
    fun runSelfTest(xml: String, parsed: ExcelImporter.Parsed, onProgress: Progress = NoProgress): SelfTest {
        val rows = parsed.rows
        if (rows.isEmpty()) return SelfTest(false, false, "No hay filas para probar.", 0)
        val col = parsed.columns.reading
        val sample = sampleRows(rows)
        val updates = LinkedHashMap<Int, Long>()
        for (r in sample) updates[r.row] = (r.reading ?: r.previous ?: 0L) + 7L

        onProgress("Probando la exportación con lecturas de ejemplo…", 0f)
        val (patched, result) = try {
            ExcelExporter.patchSheet(xml, updates, col)
        } catch (e: Exception) {
            return SelfTest(true, false, "Error al escribir: ${e.message}", sample.size)
        }
        if (result.missingRows.isNotEmpty()) {
            return SelfTest(true, false, "No se encontró la fila ${result.missingRows.first()} en la hoja.", sample.size)
        }
        val expectedChanged = updates.keys.filter { it !in result.skippedFormula }.sorted()
        val problems = mutableListOf<String>()
        // Comparación rápida: solo se miran a fondo las filas de ejemplo y se exige que TODO lo demás sea idéntico
        // carácter por carácter. Es igual de estricta que comparar toda la hoja, pero mucho más rápida en el celular.
        // (Si alguna fila no se puede ubicar con facilidad, se compara la hoja completa como antes.)
        val cmp = quickCompare(xml, patched, updates.keys, col.uppercase(), problems)
            ?: ExcelComparer.compareSheets(xml, patched, col.uppercase(), problems, onProgress.slice(0.05f, 0.9f))
        if (!cmp.ok) return SelfTest(true, false, cmp.problems.firstOrNull() ?: "La hoja cambió fuera de la columna de lectura.", sample.size)
        if (cmp.changedRows != expectedChanged) {
            return SelfTest(true, false, "Se esperaba cambiar las filas $expectedChanged y cambiaron ${cmp.changedRows}.", sample.size)
        }
        onProgress("Comprobando que cada lectura quedó en su fila…", 0.92f)
        for (row in expectedChanged) {
            val v = updates.getValue(row)
            val cell = Regex("<c r=\"${col.uppercase()}$row\"[^>]*>\\s*<v>$v</v>\\s*</c>")
            if (!cell.containsMatchIn(patched)) return SelfTest(true, false, "La celda $col$row no quedó con el valor $v.", sample.size)
        }
        val (again, r2) = ExcelExporter.patchSheet(patched, updates, col)
        if (again != patched || r2.written != 0) {
            return SelfTest(true, false, "Al exportar dos veces el resultado cambió.", sample.size)
        }
        return SelfTest(true, true, "Escribí ${expectedChanged.size} lecturas de ejemplo: solo cambió la columna $col, en la fila correcta.", sample.size)
    }

    /** Texto de la fila [row] (de «<row r="N"» a «</row>» o «/>»), o null si no se ubica de forma sencilla. */
    private fun rowSpan(xml: String, row: Int): IntRange? {
        val tag = "<row r=\"$row\""
        val start = xml.indexOf(tag)
        if (start < 0 || xml.indexOf(tag, start + tag.length) >= 0) return null
        val tagEnd = xml.indexOf('>', start)
        if (tagEnd < 0) return null
        if (xml[tagEnd - 1] == '/') return start..tagEnd
        val close = xml.indexOf("</row>", tagEnd)
        return if (close < 0) null else start..(close + 5)
    }

    /**
     * Compara la hoja original con la parcheada mirando solo las filas de ejemplo (celda por celda) y exigiendo que el
     * resto de la hoja sea exactamente igual. null = no se pudo ubicar alguna fila; se usa la comparación completa.
     */
    private fun quickCompare(
        xa: String, xb: String, rows: Collection<Int>, col: String, problems: MutableList<String>
    ): ExcelComparer.Result? {
        val spansA = ArrayList<IntRange>(); val spansB = ArrayList<IntRange>()
        for (r in rows.sorted()) {
            spansA.add(rowSpan(xa, r) ?: return null)
            spansB.add(rowSpan(xb, r) ?: return null)
        }
        fun outside(x: String, spans: List<IntRange>): String {
            val sb = StringBuilder(x.length)
            var last = 0
            for (sp in spans.sortedBy { it.first }) { sb.append(x, last, sp.first); last = sp.last + 1 }
            sb.append(x, last, x.length)
            return sb.toString()
        }
        fun inside(x: String, spans: List<IntRange>): String =
            spans.sortedBy { it.first }.joinToString("", "<sheetData>", "</sheetData>") { x.substring(it.first, it.last + 1) }
        if (outside(xa, spansA) != outside(xb, spansB)) {
            problems.add("Cambió la hoja fuera de las filas de ejemplo.")
            return ExcelComparer.Result(false, 0, emptyList(), problems.toList())
        }
        return ExcelComparer.compareSheets(inside(xa, spansA), inside(xb, spansB), col, problems)
    }

    /** Primera, última, la del medio y la primera que ya trae lectura (hasta 5 filas distintas). */
    internal fun sampleRows(rows: List<ExcelImporter.Row>): List<ExcelImporter.Row> {
        val picks = LinkedHashMap<Int, ExcelImporter.Row>()
        fun add(r: ExcelImporter.Row?) { if (r != null) picks.putIfAbsent(r.row, r) }
        add(rows.first()); add(rows.last()); add(rows[rows.size / 2]); add(rows[rows.size / 4])
        add(rows.firstOrNull { it.reading != null })
        return picks.values.toList()
    }

    private const val MAX_ROWS = 12
}
