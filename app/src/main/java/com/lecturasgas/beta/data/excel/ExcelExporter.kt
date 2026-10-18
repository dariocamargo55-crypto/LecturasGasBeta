package com.lecturasgas.beta.data.excel

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Exporta el Excel ORIGINAL de la empresa cambiando ÚNICAMENTE la celda de
 * lectura actual (columna D) de las filas que tienen lectura tomada en la app.
 *
 * Garantías (probadas con el archivo real "Lecturas A sep"):
 *  - Todos los demás archivos internos del .xlsx se copian byte a byte
 *    (estilos, tema, textos, protección de hoja, validaciones, fórmulas...).
 *  - Dentro de la hoja solo se reescribe la celda D de esas filas; el resto
 *    del XML se copia igual, carácter por carácter.
 *  - Si la lectura de la app es igual a la del Excel, la celda no se toca.
 *  - Si una celda D tuviera fórmula, NO se sobrescribe (se informa).
 *  - Se hace en UNA sola pasada sobre el texto (rápido en celulares viejos).
 */
object ExcelExporter {

    data class Result(
        val written: Int,
        val unchanged: Int,
        val skippedFormula: List<Int>,
        val missingRows: List<Int>
    )

    fun export(
        original: File,
        output: OutputStream,
        updates: Map<Int, Long>,
        readingColumn: String,
        onProgress: Progress = NoProgress
    ): Result {
        var result = Result(0, 0, emptyList(), emptyList())
        ZipFile(original).use { zip ->
            val totalEntries = zip.size().coerceAtLeast(1)
            var entryIndex = 0
            val sheetPath = XlsxPaths.firstSheetPath(zip)
            ZipOutputStream(BufferedOutputStream(output)).use { zout ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    onProgress("Escribiendo el Excel…", entryIndex.toFloat() / totalEntries)
                    entryIndex++
                    zout.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    if (entry.name == sheetPath) {
                        val xml = zip.getInputStream(entry).use {
                            String(it.readBytes(), StandardCharsets.UTF_8)
                        }
                        val base = (entryIndex - 1).toFloat() / totalEntries
                        val patched = patchSheet(xml, updates, readingColumn) { f ->
                            onProgress("Escribiendo las lecturas en la hoja…", base + f / totalEntries)
                        }
                        result = patched.second
                        zout.write(patched.first.toByteArray(StandardCharsets.UTF_8))
                    } else {
                        zip.getInputStream(entry).use { it.copyTo(zout) }
                    }
                    zout.closeEntry()
                }
            }
        }
        return result
    }

    /** Devuelve el XML parcheado y el resumen. Público para poder probarlo. */
    fun patchSheet(
        xml: String,
        updates: Map<Int, Long>,
        column: String,
        onProgress: (Float) -> Unit = {}
    ): Pair<String, Result> {
        if (updates.isEmpty()) return Pair(xml, Result(0, 0, emptyList(), emptyList()))

        val columnIndex = XlsxPaths.columnNumber(column)
        val defaultStyle = columnDefaultStyle(xml, columnIndex)
        val pending = HashSet(updates.keys)

        val sb = StringBuilder(xml.length + updates.size * 40)
        var copiedUntil = 0
        var searchFrom = xml.indexOf("<sheetData").let { if (it < 0) 0 else it }
        var written = 0
        var unchanged = 0
        val skipped = mutableListOf<Int>()
        var lastRow = 0
        var rowsSeen = 0
        val xmlLen = xml.length.coerceAtLeast(1)

        while (true) {
            val rowStart = xml.indexOf("<row", searchFrom)
            if (++rowsSeen % 500 == 0 && rowStart > 0) onProgress(rowStart.toFloat() / xmlLen)
            if (rowStart < 0) break
            // Evita confundir "<rowBreaks" u otras etiquetas con "<row".
            val after = xml.getOrNull(rowStart + 4)
            if (after != ' ' && after != '>' && after != '/') {
                searchFrom = rowStart + 4
                continue
            }
            val tagEnd = xml.indexOf('>', rowStart)
            if (tagEnd < 0) break

            val openTag = xml.substring(rowStart, tagEnd + 1)
            // Si la fila no trae su número (r), se deduce por orden, igual que al importar.
            val rowNumber = XlsxPaths.attr(openTag, "r")?.toIntOrNull() ?: (lastRow + 1)
            lastRow = rowNumber
            val selfClosing = xml[tagEnd - 1] == '/'

            val bodyEnd: Int
            val rowEnd: Int
            if (selfClosing) {
                bodyEnd = tagEnd + 1
                rowEnd = tagEnd + 1
            } else {
                val close = xml.indexOf("</row>", tagEnd)
                if (close < 0) break
                bodyEnd = close
                rowEnd = close + 6
            }
            searchFrom = rowEnd

            val newReading = updates[rowNumber]
            if (newReading == null) continue
            pending.remove(rowNumber)

            val body = if (selfClosing) "" else xml.substring(tagEnd + 1, bodyEnd)
            val patched = patchRowBody(body, "$column$rowNumber", columnIndex, newReading, defaultStyle)

            when {
                patched == null -> skipped.add(rowNumber)
                patched == body -> unchanged++
                else -> {
                    sb.append(xml, copiedUntil, rowStart)
                    if (selfClosing) {
                        sb.append(openTag, 0, openTag.length - 2).append('>')
                    } else {
                        sb.append(openTag)
                    }
                    sb.append(patched).append("</row>")
                    copiedUntil = rowEnd
                    written++
                }
            }
        }
        sb.append(xml, copiedUntil, xml.length)
        return Pair(sb.toString(), Result(written, unchanged, skipped, pending.sorted()))
    }

    /** Nuevo cuerpo de la fila, el mismo si no hay cambio, o null si D tiene fórmula (o la fila trae celdas sin referencia). */
    private fun patchRowBody(
        body: String,
        ref: String,
        columnIndex: Int,
        reading: Long,
        defaultStyle: String?
    ): String? {
        var insertAt = -1
        var pos = XlsxPaths.nextCellStart(body, 0)
        while (pos >= 0) {
            val tagEnd = body.indexOf('>', pos)
            if (tagEnd < 0) break
            val tag = body.substring(pos, tagEnd + 1)
            val cellRef = XlsxPaths.attr(tag, "r") ?: ""
            // Celda sin referencia (formato poco común): no se puede saber cuál es la D; no se toca la fila.
            if (cellRef.isEmpty()) return null
            val cellEnd = if (tag.endsWith("/>")) {
                tagEnd + 1
            } else {
                val close = body.indexOf("</c>", tagEnd)
                if (close < 0) break
                close + 4
            }

            if (cellRef == ref) {
                val cell = body.substring(pos, cellEnd)
                if (cell.contains("<f")) return null
                val oldValue = cell.substringAfter("<v>", "").substringBefore("</v>", "").trim()
                val isNumeric = XlsxPaths.attr(tag, "t") == null || XlsxPaths.attr(tag, "t") == "n"
                val oldReading = if (isNumeric) {
                    runCatching { BigDecimal(oldValue).toLong() }.getOrNull()
                } else null
                if (oldReading == reading) return body
                val newCell = buildCell(ref, XlsxPaths.attr(tag, "s"), reading)
                return body.substring(0, pos) + newCell + body.substring(cellEnd)
            }

            if (insertAt < 0 && XlsxPaths.columnNumber(XlsxPaths.columnLetters(cellRef)) > columnIndex) {
                insertAt = pos
            }
            pos = XlsxPaths.nextCellStart(body, cellEnd)
        }

        // La celda no existe físicamente: se crea en su lugar (orden de columnas)
        // con el estilo por defecto de la columna, que es como se ve vacía en Excel.
        val newCell = buildCell(ref, defaultStyle, reading)
        return if (insertAt >= 0) {
            body.substring(0, insertAt) + newCell + body.substring(insertAt)
        } else {
            body + newCell
        }
    }

    private fun buildCell(ref: String, style: String?, reading: Long): String {
        val s = if (style != null) " s=\"$style\"" else ""
        return "<c r=\"$ref\"$s><v>$reading</v></c>"
    }

    private fun columnDefaultStyle(xml: String, columnIndex: Int): String? {
        val colsStart = xml.indexOf("<cols>")
        if (colsStart < 0) return null
        val colsEnd = xml.indexOf("</cols>", colsStart)
        if (colsEnd < 0) return null
        for (tag in XlsxPaths.findTags(xml.substring(colsStart, colsEnd), "<col ")) {
            val min = XlsxPaths.attr(tag, "min")?.toIntOrNull() ?: continue
            val max = XlsxPaths.attr(tag, "max")?.toIntOrNull() ?: continue
            if (columnIndex in min..max) return XlsxPaths.attr(tag, "style")
        }
        return null
    }
}
