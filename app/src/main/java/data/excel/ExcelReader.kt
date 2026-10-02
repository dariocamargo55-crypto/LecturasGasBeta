package com.lecturasgas.beta.data.excel

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.lecturasgas.beta.data.model.MeterRecord
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ExcelReader {

    private const val ORIGINAL_FILE =
        "excel_original.xlsx"

    private fun readZipEntry(
        zip: ZipFile,
        path: String
    ): ByteArray {
        val entry =
            zip.getEntry(path)
                ?: throw IllegalStateException(
                    "No se encontró $path dentro del archivo Excel."
                )

        return zip.getInputStream(entry).use {
            it.readBytes()
        }
    }

    private fun loadSharedStrings(
        bytes: ByteArray
    ): List<String> {
        val parser =
            Xml.newPullParser()

        parser.setInput(
            ByteArrayInputStream(bytes),
            "UTF-8"
        )

        val result =
            mutableListOf<String>()

        var event =
            parser.eventType

        while (
            event != XmlPullParser.END_DOCUMENT
        ) {
            if (
                event == XmlPullParser.START_TAG &&
                parser.name == "si"
            ) {
                result.add(
                    readSharedString(parser)
                )
            }

            event =
                parser.next()
        }

        return result
    }

    private fun readSharedString(
        parser: XmlPullParser
    ): String {
        val stringDepth =
            parser.depth

        val result =
            StringBuilder()

        var event =
            parser.next()

        while (
            !(event == XmlPullParser.END_TAG &&
                    parser.depth == stringDepth &&
                    parser.name == "si")
        ) {
            if (
                event == XmlPullParser.START_TAG &&
                parser.name == "t"
            ) {
                result.append(
                    parser.nextText()
                )
            }

            event =
                parser.next()
        }

        return result.toString()
    }

    private fun getColumnName(
        cellReference: String
    ): String {
        return cellReference.takeWhile {
            it.isLetter()
        }
    }

    private fun normalizeMeter(
        value: String
    ): String {
        val cleaned =
            value.trim()

        if (cleaned.isBlank()) {
            return ""
        }

        return runCatching {
            if (
                cleaned.contains(
                    "E",
                    ignoreCase = true
                )
            ) {
                java.math.BigDecimal(
                    cleaned
                ).toPlainString()
            } else {
                cleaned.removeSuffix(".0")
            }
        }.getOrElse {
            cleaned
        }.trim()
    }

    private fun parseLongValue(
        value: String
    ): Long? {
        val cleaned =
            value.trim()

        if (cleaned.isBlank()) {
            return null
        }

        return runCatching {
            java.math.BigDecimal(
                cleaned
            ).toLong()
        }.getOrNull()
    }

    /*
     * Obtiene el nombre del archivo Excel seleccionado
     * y lo convierte en el nombre visible de la ruta.
     *
     * Ejemplo:
     *
     * ruta A septiembre.xlsx
     *
     * se convierte en:
     *
     * RUTA A — SEPTIEMBRE
     */
    fun getRouteName(
        context: Context,
        uri: Uri
    ): String? {

        var fileName: String? = null

        val cursor: Cursor? =
            context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )

        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex =
                    it.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (columnIndex >= 0) {
                    fileName =
                        it.getString(
                            columnIndex
                        )
                }
            }
        }

        if (fileName.isNullOrBlank()) {
            fileName =
                uri.lastPathSegment
        }

        if (fileName.isNullOrBlank()) {
            return null
        }

        var cleanName =
            fileName
                .trim()
                .replace(
                    Regex("""\.[^.]+$"""),
                    ""
                )
                .replace(
                    Regex("""\s+"""),
                    " "
                )
                .trim()

        if (cleanName.isBlank()) {
            return null
        }

        cleanName =
            cleanName.uppercase(
                Locale.ROOT
            )

        val routePattern =
            Regex(
                """^RUTA\s+([A-Z0-9]+)\s+(.+)$"""
            )

        val match =
            routePattern.matchEntire(
                cleanName
            )

        return if (match != null) {
            val route =
                "RUTA ${match.groupValues[1]}"

            val period =
                match.groupValues[2].trim()

            "$route — $period"
        } else {
            cleanName
        }
    }

    private fun readSheet(
        bytes: ByteArray,
        sharedStrings: List<String>
    ): List<MeterRecord> {
        val parser =
            Xml.newPullParser()

        parser.setInput(
            ByteArrayInputStream(bytes),
            "UTF-8"
        )

        val records =
            mutableListOf<MeterRecord>()

        var event =
            parser.eventType

        while (
            event != XmlPullParser.END_DOCUMENT
        ) {
            if (
                event == XmlPullParser.START_TAG &&
                parser.name == "row"
            ) {
                val rowNumber =
                    parser
                        .getAttributeValue(
                            null,
                            "r"
                        )
                        ?.toIntOrNull()
                        ?: 0

                val rowDepth =
                    parser.depth

                val cells =
                    mutableMapOf<String, String>()

                event =
                    parser.next()

                while (
                    !(event == XmlPullParser.END_TAG &&
                            parser.depth == rowDepth &&
                            parser.name == "row")
                ) {
                    if (
                        event == XmlPullParser.START_TAG &&
                        parser.name == "c"
                    ) {
                        val cellReference =
                            parser.getAttributeValue(
                                null,
                                "r"
                            ) ?: ""

                        val cellType =
                            parser.getAttributeValue(
                                null,
                                "t"
                            )

                        val cellDepth =
                            parser.depth

                        var value =
                            ""

                        var innerEvent =
                            parser.next()

                        while (
                            !(innerEvent == XmlPullParser.END_TAG &&
                                    parser.depth == cellDepth &&
                                    parser.name == "c")
                        ) {
                            if (
                                innerEvent == XmlPullParser.START_TAG &&
                                parser.name == "v"
                            ) {
                                value =
                                    parser.nextText()
                            }

                            innerEvent =
                                parser.next()
                        }

                        if (
                            cellType == "s" &&
                            value.isNotBlank()
                        ) {
                            val sharedIndex =
                                value.toIntOrNull()

                            if (
                                sharedIndex != null
                            ) {
                                value =
                                    sharedStrings
                                        .getOrNull(
                                            sharedIndex
                                        )
                                        ?: value
                            }
                        }

                        cells[
                            getColumnName(
                                cellReference
                            )
                        ] = value
                    }

                    event =
                        parser.next()
                }

                /*
                 * La fila 1 contiene los encabezados.
                 *
                 * Cada fila posterior que tenga un medidor
                 * representa un registro independiente.
                 *
                 * rowNumber conserva la posición original
                 * del registro dentro del Excel.
                 *
                 * Esto permite conservar medidores duplicados.
                 */
                if (
                    rowNumber > 1 &&
                    cells["C"]
                        .orEmpty()
                        .isNotBlank()
                ) {
                    records.add(
                        MeterRecord(
                            rowNumber =
                                rowNumber,

                            nir =
                                cells["A"]
                                    .orEmpty(),

                            address =
                                cells["B"]
                                    .orEmpty(),

                            meter =
                                normalizeMeter(
                                    cells["C"]
                                        .orEmpty()
                                ),

                            previousReading =
                                parseLongValue(
                                    cells["F"]
                                        .orEmpty()
                                ),

                            neighborhood =
                                cells["G"]
                                    .orEmpty(),

                            user =
                                cells["H"]
                                    .orEmpty(),

                            observation =
                                cells["I"]
                                    .orEmpty(),

                            currentReading =
                                parseLongValue(
                                    cells["D"]
                                        .orEmpty()
                                )
                        )
                    )
                }
            }

            event =
                parser.next()
        }

        return records
    }

    fun read(
        context: Context,
        uri: Uri
    ): List<MeterRecord> {
        val temporaryFile =
            File.createTempFile(
                "lecturas_import_",
                ".xlsx",
                context.cacheDir
            )

        try {
            context.contentResolver
                .openInputStream(uri)
                .use { input ->

                    requireNotNull(input) {
                        "No se pudo abrir el archivo Excel."
                    }

                    temporaryFile
                        .outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }

            ZipFile(
                temporaryFile
            ).use { zip ->

                val sharedStrings =
                    if (
                        zip.getEntry(
                            "xl/sharedStrings.xml"
                        ) != null
                    ) {
                        loadSharedStrings(
                            readZipEntry(
                                zip,
                                "xl/sharedStrings.xml"
                            )
                        )
                    } else {
                        emptyList()
                    }

                val sheetBytes =
                    readZipEntry(
                        zip,
                        "xl/worksheets/sheet1.xml"
                    )

                return readSheet(
                    sheetBytes,
                    sharedStrings
                )
            }
        } finally {
            temporaryFile.delete()
        }
    }

    fun copyOriginal(
        context: Context,
        uri: Uri
    ) {
        context.contentResolver
            .openInputStream(uri)
            .use { input ->

                requireNotNull(input) {
                    "No se pudo abrir el Excel original."
                }

                File(
                    context.filesDir,
                    ORIGINAL_FILE
                ).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
    }

    fun export(
        context: Context,
        records: List<MeterRecord>,
        destination: Uri
    ) {
        val original =
            File(
                context.filesDir,
                ORIGINAL_FILE
            )

        require(original.exists()) {
            "Primero debes importar un Excel."
        }

        val updates =
            records
                .filter {
                    it.currentReading != null
                }
                .associate {
                    it.rowNumber to
                            it.currentReading!!
                }

        ZipFile(original).use { zip ->

            context.contentResolver
                .openOutputStream(destination)
                .use { output ->

                    requireNotNull(output) {
                        "No se pudo crear el archivo."
                    }

                    ZipOutputStream(
                        BufferedOutputStream(output)
                    ).use { zout ->

                        val entries =
                            zip.entries()

                        while (
                            entries.hasMoreElements()
                        ) {
                            val entry =
                                entries.nextElement()

                            val newEntry =
                                ZipEntry(
                                    entry.name
                                ).apply {
                                    time =
                                        entry.time
                                }

                            zout.putNextEntry(
                                newEntry
                            )

                            val data =
                                zip.getInputStream(
                                    entry
                                ).use {
                                    it.readBytes()
                                }

                            val outputData =
                                if (
                                    entry.name ==
                                    "xl/worksheets/sheet1.xml"
                                ) {
                                    updateSheetXml(
                                        data,
                                        updates
                                    )
                                } else {
                                    data
                                }

                            zout.write(
                                outputData
                            )

                            zout.closeEntry()
                        }
                    }
                }
        }
    }

    private fun updateSheetXml(
        bytes: ByteArray,
        updates: Map<Int, Long>
    ): ByteArray {

        var xml =
            bytes.toString(
                StandardCharsets.UTF_8
            )

        for (
        (row, reading) in updates
        ) {
            val rowRegex =
                Regex(
                    """(<row\b[^>]*\br="$row"[^>]*>)(.*?)(</row>)""",
                    RegexOption.DOT_MATCHES_ALL
                )

            val rowMatch =
                rowRegex.find(
                    xml
                )
                    ?: continue

            val body =
                rowMatch.groupValues[2]

            val cellRegex =
                Regex(
                    """<c\b[^>]*?\br="D$row"[^>]*?(?:/>|>.*?</c>)""",
                    RegexOption.DOT_MATCHES_ALL
                )

            val cellMatch =
                cellRegex.find(
                    body
                )

            if (cellMatch == null) {
                continue
            }

            val originalCell =
                cellMatch.value

            val originalValue =
                Regex(
                    """<v>(.*?)</v>""",
                    RegexOption.DOT_MATCHES_ALL
                )
                    .find(
                        originalCell
                    )
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()

            val originalReading =
                originalValue?.let {
                    runCatching {
                        java.math.BigDecimal(
                            it
                        ).toLong()
                    }.getOrNull()
                }

            /*
             * Si la lectura que tiene la aplicación es
             * exactamente la misma que ya estaba en el
             * Excel original, no tocamos absolutamente
             * nada de esa celda.
             *
             * Esto evita reconstruir lecturas que ya
             * existían y conserva su representación,
             * formato y XML original.
             */
            if (
                originalReading == reading
            ) {
                continue
            }

            val newCell =
                if (
                    originalCell.contains(
                        "<v>"
                    )
                ) {
                    val valueMatch =
                        Regex(
                            """<v>.*?</v>""",
                            RegexOption.DOT_MATCHES_ALL
                        ).find(
                            originalCell
                        )

                    if (valueMatch != null) {
                        originalCell.replaceRange(
                            valueMatch.range,
                            "<v>$reading</v>"
                        )
                    } else {
                        originalCell
                    }
                } else if (
                    originalCell.endsWith(
                        "/>"
                    )
                ) {
                    originalCell
                        .removeSuffix(
                            "/>"
                        ) +
                            "><v>$reading</v></c>"
                } else {
                    originalCell
                        .replace(
                            "</c>",
                            "<v>$reading</v></c>"
                        )
                }

            val newBody =
                body.replaceRange(
                    cellMatch.range,
                    newCell
                )

            xml =
                xml.replaceRange(
                    rowMatch.range,
                    rowMatch.groupValues[1] +
                            newBody +
                            rowMatch.groupValues[3]
                )
        }

        return xml.toByteArray(
            StandardCharsets.UTF_8
        )
    }
}
