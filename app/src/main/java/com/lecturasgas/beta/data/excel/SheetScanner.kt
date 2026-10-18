package com.lecturasgas.beta.data.excel

/**
 * Revisa el XML de la hoja del Excel (sin interpretar textos) y cuenta cosas que podrían
 * estropear la lectura o la exportación: fórmulas o textos en la columna de lectura, celdas
 * combinadas, filas sin número, etc. Es código puro (sin Android) y se prueba en la computadora.
 */
object SheetScanner {

    data class Scan(
        /** Filas que tienen al menos una celda con valor (incluye el encabezado). */
        val rowsWithContent: Set<Int>,
        /** Etiquetas <row> sin atributo r (el número de fila se deduce por orden). */
        val rowsWithoutNumber: Int,
        /** Etiquetas <c> sin atributo r. */
        val cellsWithoutRef: Int,
        /** El XML usa prefijos de espacio de nombres (<x:row>): la app no sabría leerlo ni escribirlo. */
        val prefixedTags: Boolean,
        /** Filas cuya celda de lectura tiene una fórmula. */
        val formulaRows: List<Int>,
        /** Filas cuya celda de lectura guarda el número como texto. */
        val textRows: List<Int>,
        /** Filas cuya celda de lectura tiene decimales (la app trabaja con enteros). */
        val decimalRows: List<Int>,
        /** Celdas combinadas que tocan la columna de lectura. */
        val mergedOnReading: Int,
        val hasSheetData: Boolean
    )

    private val MERGE = Regex("<mergeCell\\s+ref=\"([A-Z]+)(\\d+):([A-Z]+)(\\d+)\"")

    fun scan(xml: String, readingColumn: String): Scan {
        val colIndex = XlsxPaths.columnNumber(readingColumn)
        val content = sortedSetOf<Int>()
        val formulas = mutableListOf<Int>()
        val texts = mutableListOf<Int>()
        val decimals = mutableListOf<Int>()
        var rowsNoNumber = 0
        var cellsNoRef = 0

        val dataStart = xml.indexOf("<sheetData")
        var searchFrom = if (dataStart < 0) 0 else dataStart
        var lastRow = 0
        while (true) {
            val rowStart = xml.indexOf("<row", searchFrom)
            if (rowStart < 0) break
            val after = xml.getOrNull(rowStart + 4)
            if (after != ' ' && after != '>' && after != '/') { searchFrom = rowStart + 4; continue }
            val tagEnd = xml.indexOf('>', rowStart)
            if (tagEnd < 0) break
            val openTag = xml.substring(rowStart, tagEnd + 1)
            val declared = XlsxPaths.attr(openTag, "r")?.toIntOrNull()
            if (declared == null) rowsNoNumber++
            val rowNumber = declared ?: (lastRow + 1)
            lastRow = rowNumber
            if (xml[tagEnd - 1] == '/') { searchFrom = tagEnd + 1; continue }
            val close = xml.indexOf("</row>", tagEnd)
            if (close < 0) break
            searchFrom = close + 6
            val body = xml.substring(tagEnd + 1, close)

            var pos = XlsxPaths.nextCellStart(body, 0)
            while (pos >= 0) {
                val cTagEnd = body.indexOf('>', pos)
                if (cTagEnd < 0) break
                val tag = body.substring(pos, cTagEnd + 1)
                val selfClosing = tag.endsWith("/>")
                val cellEnd = if (selfClosing) cTagEnd + 1 else {
                    val c = body.indexOf("</c>", cTagEnd)
                    if (c < 0) break
                    c + 4
                }
                val cell = body.substring(pos, cellEnd)
                val ref = XlsxPaths.attr(tag, "r")
                if (ref == null) cellsNoRef++
                val value = cell.substringAfter("<v>", "").substringBefore("</v>", "").trim()
                val hasValue = value.isNotEmpty() || cell.contains("<is>") || cell.contains("<f")
                if (hasValue) content.add(rowNumber)
                if (ref != null && XlsxPaths.columnNumber(XlsxPaths.columnLetters(ref)) == colIndex && rowNumber > 0) {
                    val type = XlsxPaths.attr(tag, "t")
                    when {
                        cell.contains("<f") -> formulas.add(rowNumber)
                        type == "s" || type == "str" || type == "inlineStr" -> if (hasValue) texts.add(rowNumber)
                        value.isNotEmpty() && isFractional(value) -> decimals.add(rowNumber)
                    }
                }
                pos = XlsxPaths.nextCellStart(body, cellEnd)
            }
        }

        var merged = 0
        for (m in MERGE.findAll(xml)) {
            val c1 = XlsxPaths.columnNumber(m.groupValues[1]); val r1 = m.groupValues[2].toInt()
            val c2 = XlsxPaths.columnNumber(m.groupValues[3]); val r2 = m.groupValues[4].toInt()
            if (colIndex in c1..c2 && (c1 != c2 || r1 != r2)) merged++
        }

        return Scan(
            rowsWithContent = content,
            rowsWithoutNumber = rowsNoNumber,
            cellsWithoutRef = cellsNoRef,
            prefixedTags = Regex("<[A-Za-z0-9]+:(row|c|sheetData)[\\s>]").containsMatchIn(xml),
            formulaRows = formulas,
            textRows = texts,
            decimalRows = decimals,
            mergedOnReading = merged,
            hasSheetData = dataStart >= 0
        )
    }

    /** "1146.5" -> true ; "1146.0" -> false ; "1.2E3" -> false (1200). */
    private fun isFractional(v: String): Boolean = runCatching {
        val d = java.math.BigDecimal(v)
        d.stripTrailingZeros().scale() > 0
    }.getOrDefault(false)
}
