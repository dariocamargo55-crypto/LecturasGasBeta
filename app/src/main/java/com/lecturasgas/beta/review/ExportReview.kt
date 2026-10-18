package com.lecturasgas.beta.review

import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.readings.ConsumptionCheck
import com.lecturasgas.beta.util.TextUtil

/**
 * Revisión ANTES de exportar: junta en una lista lo que conviene mirar dos veces
 * (lecturas menores a la anterior, consumos exagerados, lecturas del Excel que se van a cambiar,
 * medidores sin leer…). Solo mira lo que la app va a escribir. Es código puro (sin Android).
 */
object ExportReview {

    enum class Level { ERROR, WARNING, INFO }

    enum class Kind(val level: Level, val title: String, val hint: String) {
        NEGATIVE(Level.ERROR, "Lectura menor a la anterior",
            "Casi siempre es un dígito mal escrito. Toca una para corregirla."),
        HIGH(Level.WARNING, "Consumo muy alto",
            "Mucho mayor que lo normal de esta ruta. Puede ser un dígito de más o una fuga."),
        OVERWRITE(Level.WARNING, "Cambia una lectura que ya traía el Excel",
            "El Excel tenía otra lectura en esa fila; la de la app la reemplaza."),
        ZERO(Level.INFO, "Consumo cero",
            "La lectura es igual a la anterior (casa vacía, medidor parado o lectura repetida)."),
        METER_FIX(Level.INFO, "Número de medidor corregido",
            "El Excel conserva el número original: la corrección queda solo en la app."),
        SKIPPED(Level.INFO, "No se pudo leer",
            "Quedan vacías en el Excel."),
        UNREAD(Level.INFO, "Sin leer",
            "Quedan vacías en el Excel.")
    }

    data class Item(val row: Int, val nir: String, val meter: String, val user: String, val detail: String)

    data class Section(val kind: Kind, val items: List<Item>)

    data class Result(
        val sections: List<Section>,
        val total: Int,
        /** Lecturas tomadas con la app: las que se escribirán en el Excel. */
        val toWrite: Int,
        /** Consumo típico de la ruta (mediana), si hay suficientes datos. */
        val typicalConsumption: Long?
    ) {
        fun section(kind: Kind): Section? = sections.firstOrNull { it.kind == kind }
        fun count(kind: Kind): Int = section(kind)?.items?.size ?: 0
        val errorCount: Int get() = sections.filter { it.kind.level == Level.ERROR }.sumOf { it.items.size }
        val warningCount: Int get() = sections.filter { it.kind.level == Level.WARNING }.sumOf { it.items.size }
        val hasErrors: Boolean get() = errorCount > 0
        val hasWarnings: Boolean get() = warningCount > 0
        /** Nada raro: ni errores ni avisos (los datos informativos no cuentan). */
        val clean: Boolean get() = !hasErrors && !hasWarnings
    }

    fun analyze(records: List<MeterRecord>): Result {
        // Mismos umbrales que el aviso de la tarjeta de lectura, para que nunca se contradigan.
        val typical = ConsumptionCheck.typical(records)
        val highLimit = ConsumptionCheck.highThreshold(typical)

        val lists = Kind.values().associateWith { mutableListOf<Item>() }
        fun add(kind: Kind, r: MeterRecord, detail: String) {
            lists.getValue(kind).add(Item(r.row, r.nir, r.displayMeter, r.user, detail))
        }

        var toWrite = 0
        for (r in records) {
            val app = r.appReading
            if (app != null) {
                toWrite++
                val prev = r.previous
                if (prev != null) {
                    val c = app - prev
                    if (c < 0) {
                        val fewerDigits = digitCount(app) < digitCount(prev)
                        add(Kind.NEGATIVE, r, "Anterior $prev → nueva $app ($c)" + if (fewerDigits) " · ¿faltó un dígito?" else "")
                    } else if (c == 0L) {
                        add(Kind.ZERO, r, "Anterior $prev = nueva $app")
                    } else if (c > highLimit) {
                        add(Kind.HIGH, r, "Consumo $c" + (typical?.let { " · lo típico en la ruta es ≈ $it" } ?: ""))
                    }
                }
                val old = r.excelReading
                if (old != null && old != app) add(Kind.OVERWRITE, r, "El Excel decía $old → quedará $app")
            } else if (r.excelReading == null) {
                if (r.isSkipped) add(Kind.SKIPPED, r, r.skipReason.orEmpty())
                else add(Kind.UNREAD, r, if (r.isNew) "Medidor nuevo" else "Pendiente")
            }
            if (r.hasMeterFix) add(Kind.METER_FIX, r, "Excel: ${r.meter} → real: ${r.realMeter}")
        }

        val sections = Kind.values().mapNotNull { k ->
            val items = lists.getValue(k).sortedBy { it.row }
            if (items.isEmpty()) null else Section(k, items)
        }
        return Result(sections, records.size, toWrite, typical)
    }

    private fun digitCount(n: Long): Int = TextUtil.digits(n.toString()).length
}
