package com.lecturasgas.beta.review

import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.util.TimeUtil

/**
 * Respaldo de lecturas en TEXTO, pensado para leerse (en el computador o en el celular):
 * un encabezado con la ruta y la fecha, un resumen, lo que conviene revisar y la lista completa
 * en el mismo orden de las filas del Excel, con las columnas alineadas y una línea de separación
 * cada 10 filas. Es código puro (sin Android) para poder probarlo.
 */
object ReadingsReport {

    private const val W = 111
    private const val SEP = " │ "

    fun build(routeName: String, period: String, records: List<MeterRecord>, now: Long): String {
        val taken = records.filter { it.appReading != null }.sortedBy { it.row }
        val review = ExportReview.analyze(records)
        val sb = StringBuilder()
        fun line(text: String = "") { sb.append(text).append("\r\n") }
        fun heavy() = line("═".repeat(W))
        fun light() = line("─".repeat(W))

        // ---- Encabezado ----
        heavy()
        line("  RESPALDO DE LECTURAS")
        line("  Ruta: $routeName" + if (period.isNotBlank()) "   (periodo $period)" else "")
        line("  Generado: ${TimeUtil.dateTime(now)}")
        heavy()
        line()

        // ---- Resumen ----
        val unread = records.count { it.reading == null }
        val skipped = records.count { it.isSkipped }
        line("RESUMEN")
        fun kv(k: String, v: String) = line("  " + k.padEnd(34, '.') + " " + v)
        kv("Lecturas tomadas con la app", "${taken.size} de ${records.size} medidores")
        val already = records.count { it.appReading == null && it.excelReading != null }
        if (already > 0) kv("Ya venían en el Excel", "$already")
        kv("Sin lectura", "$unread" + if (skipped > 0) "  (de ellas, $skipped con motivo de no lectura)" else "")
        review.typicalConsumption?.let { kv("Consumo típico de la ruta", "≈ $it") }
        val times = taken.mapNotNull { it.readAt }
        if (times.isNotEmpty()) {
            kv("Primera lectura", TimeUtil.dateTime(times.min()))
            kv("Última lectura", TimeUtil.dateTime(times.max()))
        }
        line()

        // ---- Para revisar ----
        val flagged = listOf(
            ExportReview.Kind.NEGATIVE to "LECTURAS MENORES A LA ANTERIOR (casi siempre un dígito mal escrito)",
            ExportReview.Kind.HIGH to "CONSUMO MUY ALTO (posible fuga o un dígito de más)",
            ExportReview.Kind.ZERO to "CONSUMO CERO (igual a la anterior)",
            ExportReview.Kind.OVERWRITE to "CAMBIAN UNA LECTURA QUE YA TRAÍA EL EXCEL",
            ExportReview.Kind.METER_FIX to "NÚMERO DE MEDIDOR CORREGIDO (el Excel conserva el original)"
        ).mapNotNull { (k, title) -> review.section(k)?.let { Triple(k, title, it.items) } }
        if (flagged.isNotEmpty()) {
            line("PARA REVISAR")
            light()
            for ((_, title, items) in flagged) {
                line("  ${title} · ${items.size}")
                for (it in items.take(MAX_FLAGGED)) {
                    line("     Fila ${it.row.toString().padStart(5)}${SEP}NIR ${it.nir.padEnd(6)}${SEP}${it.meter.padEnd(12)}${SEP}${fit(it.user, 22).padEnd(22)}${SEP}${it.detail}")
                }
                if (items.size > MAX_FLAGGED) line("     … y ${items.size - MAX_FLAGGED} más (están en la lista completa de abajo)")
                line()
            }
        }

        // ---- Lista completa ----
        line("LECTURAS TOMADAS  (en el orden de las filas del Excel; «Orden» = en qué lugar se tomó cada una)")
        light()
        line(header())
        light()
        val order = ReadingOrder.sequence(records)
        val flags = HashMap<Int, String>()
        for (sec in review.sections) {
            val mark = when (sec.kind) {
                ExportReview.Kind.NEGATIVE -> "▼ MENOR"
                ExportReview.Kind.HIGH -> "▲ ALTO"
                ExportReview.Kind.ZERO -> "= CERO"
                else -> null
            } ?: continue
            for (it in sec.items) flags[it.row] = mark
        }
        if (taken.isEmpty()) {
            line("  (todavía no hay lecturas tomadas con la app)")
        }
        for ((i, r) in taken.withIndex()) {
            if (i > 0 && i % 10 == 0) line("·".repeat(W))
            val cons = r.previous?.let { r.appReading!! - it }
            line(
                (order[r.row]?.toString() ?: "—").padStart(5) + SEP +
                    r.row.toString().padStart(5) + SEP +
                    fit(r.nir, 6).padStart(6) + SEP +
                    fit(r.displayMeter, 12).padEnd(12) + SEP +
                    fit(r.user, 24).padEnd(24) + SEP +
                    (r.previous?.toString() ?: "—").padStart(8) + SEP +
                    r.appReading.toString().padStart(8) + SEP +
                    (cons?.let { if (it > 0) "+$it" else it.toString() } ?: "—").padStart(7) + SEP +
                    (r.readAt?.let { TimeUtil.hour(it) } ?: "").padEnd(10) + SEP.trimEnd() +
                    (flags[r.row]?.let { " $it" } ?: "")
            )
        }
        light()
        line("Total: ${taken.size} lecturas · ruta $routeName")
        line()
        line("Nota: este archivo es solo un respaldo para leer y comparar. Para pasar las lecturas a Excel usa")
        line("«Lista de lecturas (CSV)»; el Excel de la empresa se genera con «Exportar Excel».")
        return sb.toString()
    }

    private fun header(): String =
        "Orden".padStart(5) + SEP + "Fila".padStart(5) + SEP + "NIR".padStart(6) + SEP + "Medidor".padEnd(12) + SEP + "Usuario".padEnd(24) + SEP +
            "Anterior".padStart(8) + SEP + "Lectura".padStart(8) + SEP + "Consumo".padStart(7) + SEP + "Hora".padEnd(10) + SEP.trimEnd()

    /** Recorta con «…» si no cabe. */
    private fun fit(text: String, n: Int): String {
        val t = text.replace('\t', ' ').replace('\n', ' ').trim()
        return if (t.length <= n) t else t.take(n - 1) + "…"
    }

    private const val MAX_FLAGGED = 40
}
