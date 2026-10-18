package com.lecturasgas.beta.data.excel

import java.io.File
import java.util.zip.ZipFile

/**
 * Compara un Excel exportado contra el ORIGINAL de la empresa y dice exactamente qué cambió.
 * Lo correcto es que lo único distinto sean celdas de la columna de lectura actual.
 * Es código puro (sin Android), así se puede probar en la computadora.
 */
object ExcelComparer {

    data class Result(
        /** true = lo único distinto son celdas de la columna de lectura. */
        val ok: Boolean,
        /** Celdas de la columna de lectura que tienen un valor distinto al original. */
        val changedCells: Int,
        /** Filas del Excel donde cambió la lectura (en orden). */
        val changedRows: List<Int>,
        /** Explicaciones de cada diferencia inesperada (máximo unas pocas). */
        val problems: List<String>
    )

    private val CELL = Regex("<c(?:\\s[^>]*?)?(?:/>|>.*?</c>)", RegexOption.DOT_MATCHES_ALL)
    private val REF = Regex("\\sr=\"([A-Z]+)(\\d+)\"")
    private val SELF_CLOSING_ROW = Regex("<row\\b([^>]*?)\\s*/>")

    fun compare(original: File, other: File, readingColumn: String, onProgress: Progress = NoProgress): Result {
        val problems = mutableListOf<String>()
        val col = readingColumn.uppercase()
        ZipFile(original).use { a ->
            ZipFile(other).use { b ->
                val namesA = a.entries().toList().map { it.name }
                val namesB = b.entries().toList().map { it.name }
                if (namesA != namesB) {
                    problems.add("El archivo tiene partes internas distintas a las del original.")
                    return Result(false, 0, emptyList(), problems)
                }
                val sheet = XlsxPaths.firstSheetPath(a)
                for ((i, name) in namesA.withIndex()) {
                    onProgress("Comparando las partes del archivo…", 0.08f * i / namesA.size)
                    if (name == sheet || name.endsWith("/")) continue
                    val ba = a.getInputStream(a.getEntry(name)).use { it.readBytes() }
                    val bb = b.getInputStream(b.getEntry(name)).use { it.readBytes() }
                    if (!ba.contentEquals(bb)) {
                        problems.add("Cambió una parte interna del Excel: $name")
                        if (problems.size >= MAX_PROBLEMS) return Result(false, 0, emptyList(), problems)
                    }
                }
                onProgress("Abriendo la hoja de lecturas…", 0.08f)
                val xa = XlsxPaths.readText(a, sheet)
                val xb = XlsxPaths.readText(b, sheet)
                return compareSheets(xa, xb, col, problems, onProgress.slice(0.10f, 1f))
            }
        }
    }

    /** Lo que se necesita de una hoja para compararla, sacado en UNA sola pasada (antes eran seis). */
    private class SheetParts(val cells: Map<String, String>, val unreferenced: List<String>, val rest: String)

    internal fun compareSheets(
        xa: String, xb: String, col: String, problems: MutableList<String>,
        onProgress: Progress = NoProgress
    ): Result {
        val partsA = split(xa) { onProgress("Leyendo las celdas del original…", 0.35f * it) }
        val partsB = split(xb) { onProgress("Leyendo las celdas del archivo…", 0.35f + 0.35f * it) }
        val cellsA = partsA.cells
        val cellsB = partsB.cells
        val changedRows = sortedSetOf<Int>()
        var changed = 0

        var n = 0
        val totalA = cellsA.size.coerceAtLeast(1)
        for ((ref, rawA) in cellsA) {
            if (++n % 2000 == 0) onProgress("Comparando celda por celda…", 0.70f + 0.25f * n / totalA)
            val rawB = cellsB[ref]
            val c = ref.takeWhile { it.isLetter() }
            val row = ref.dropWhile { it.isLetter() }.toInt()
            if (rawB == null) {
                // La celda de lectura estaba vacía y no existía: es lo mismo que no estar. Cualquier otra, es un problema.
                if (c != col || rawA.contains("<v>") || rawA.contains("<f")) {
                    problems.add("Falta la celda $ref que sí estaba en el original.")
                }
            } else if (rawA != rawB) {
                if (c == col && row > 1) { changed++; changedRows.add(row) }
                else problems.add("Cambió la celda $ref (fuera de la columna de lectura).")
            }
            if (problems.size >= MAX_PROBLEMS) break
        }
        for ((ref, rawB) in cellsB) {
            if (ref in cellsA) continue
            val c = ref.takeWhile { it.isLetter() }
            val row = ref.dropWhile { it.isLetter() }.toInt()
            if (c == col && row > 1) { changed++; changedRows.add(row) }
            else problems.add("Hay una celda nueva $ref que no estaba en el original.")
            if (problems.size >= MAX_PROBLEMS) break
        }

        onProgress("Comprobando la estructura de la hoja…", 0.97f)
        // Celdas sin referencia (formato poco común): la app no las toca, así que deben seguir idénticas y en el mismo orden.
        if (partsA.unreferenced != partsB.unreferenced) problems.add("Cambiaron celdas que no traen su referencia (formato poco común).")

        // Todo lo que NO es una celda (filas, anchos de columna, protección, validaciones…) debe ser igual.
        if (partsA.rest != partsB.rest) problems.add("Cambió la estructura de la hoja (filas, columnas o protección).")

        onProgress("Listo", 1f)
        return Result(problems.isEmpty(), changed, changedRows.toList(), problems.take(MAX_PROBLEMS))
    }

    /**
     * Una sola pasada por la hoja: las celdas con referencia (A1 → texto de la celda), las celdas sin referencia, y
     * la hoja sin sus celdas (las filas vacías «<row/>» se igualan a «<row></row>»).
     */
    private fun split(xml: String, onProgress: (Float) -> Unit = {}): SheetParts {
        val cells = LinkedHashMap<String, String>()
        val unreferenced = ArrayList<String>()
        val rest = StringBuilder(xml.length / 3)
        var last = 0
        var n = 0
        val len = xml.length.coerceAtLeast(1)
        for (m in CELL.findAll(xml)) {
            if (++n % 3000 == 0) onProgress(m.range.last.toFloat() / len)
            rest.append(xml, last, m.range.first)
            last = m.range.last + 1
            val raw = m.value
            val r = REF.find(raw.substringBefore('>'))
            if (r == null) unreferenced.add(raw) else cells[r.groupValues[1] + r.groupValues[2]] = raw
        }
        rest.append(xml, last, xml.length)
        val normalized = SELF_CLOSING_ROW.replace(rest) { "<row${it.groupValues[1]}></row>" }
        return SheetParts(cells, unreferenced, normalized)
    }

    private const val MAX_PROBLEMS = 6
}
