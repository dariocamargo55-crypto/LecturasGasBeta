package com.lecturasgas.beta.data.excel

import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * Funciones pequeñas para leer el "esqueleto" de un .xlsx.
 * Un .xlsx es un ZIP con archivos XML adentro:
 *   xl/workbook.xml           -> lista de hojas
 *   xl/_rels/workbook.xml.rels -> en qué archivo está cada hoja
 *   xl/worksheets/sheetN.xml  -> las celdas
 *   xl/sharedStrings.xml      -> los textos repetidos
 */
internal object XlsxPaths {

    private const val DEFAULT_SHEET = "xl/worksheets/sheet1.xml"

    /** Ruta interna de la PRIMERA hoja del libro (no siempre se llama sheet1.xml). */
    fun firstSheetPath(zip: ZipFile): String {
        return runCatching {
            val workbook = readText(zip, "xl/workbook.xml")
            val sheetTag = findTags(workbook, "<sheet ").firstOrNull() ?: return DEFAULT_SHEET
            val relId = attr(sheetTag, "r:id") ?: return DEFAULT_SHEET
            val rels = readText(zip, "xl/_rels/workbook.xml.rels")
            val relTag = findTags(rels, "<Relationship ").firstOrNull { attr(it, "Id") == relId }
                ?: return DEFAULT_SHEET
            val target = attr(relTag, "Target") ?: return DEFAULT_SHEET
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            if (zip.getEntry(path) != null) path else DEFAULT_SHEET
        }.getOrDefault(DEFAULT_SHEET)
    }

    /** Posición de la siguiente etiqueta <c ...> (celda) desde [from], o -1. No confunde <cols> ni <col>. */
    fun nextCellStart(body: String, from: Int): Int {
        var pos = body.indexOf("<c", from)
        while (pos >= 0) {
            val after = body.getOrNull(pos + 2)
            if (after == ' ' || after == '>' || after == '/') return pos
            pos = body.indexOf("<c", pos + 2)
        }
        return -1
    }

    fun readText(zip: ZipFile, path: String): String {
        val entry = zip.getEntry(path) ?: error("No se encontró $path dentro del Excel.")
        return zip.getInputStream(entry).use { String(it.readBytes(), StandardCharsets.UTF_8) }
    }

    fun findTags(text: String, prefix: String): List<String> {
        val out = mutableListOf<String>()
        var pos = text.indexOf(prefix)
        while (pos >= 0) {
            val end = text.indexOf('>', pos)
            if (end < 0) break
            out.add(text.substring(pos, end + 1))
            pos = text.indexOf(prefix, end)
        }
        return out
    }

    /** attr("<c r=\"D5\" s=\"4\"/>", "s") = "4" */
    fun attr(tag: String, name: String): String? {
        var from = 0
        while (true) {
            val i = tag.indexOf("$name=\"", from)
            if (i < 0) return null
            if (i == 0 || tag[i - 1] == ' ' || tag[i - 1] == '\n' || tag[i - 1] == '\t') {
                val start = i + name.length + 2
                val end = tag.indexOf('"', start)
                return if (end < 0) null else tag.substring(start, end)
            }
            from = i + 1
        }
    }

    /** "A" -> 1, "D" -> 4, "AA" -> 27 */
    fun columnNumber(letters: String): Int {
        var n = 0
        for (ch in letters) {
            val c = ch.uppercaseChar()
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else break
        }
        return n
    }

    fun columnLetters(cellRef: String): String = cellRef.takeWhile { it.isLetter() }
}
