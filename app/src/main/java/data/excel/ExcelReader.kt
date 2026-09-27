package com.lecturasgas.beta.data.excel

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.lecturasgas.beta.data.model.MeterRecord
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

object ExcelReader {

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

        return value
            .trim()
            .removeSuffix(".0")
            .trim()
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

                            input.copyTo(
                                output
                            )
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
}