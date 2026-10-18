package com.lecturasgas.beta.review

import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.util.TimeUtil

/**
 * Orden en que se fueron tomando las lecturas: la 1ª, la 2ª, la 3ª… según el momento en que se tomó cada una
 * POR PRIMERA VEZ (corregir una lectura después no la cambia de lugar). Base para generar, mes a mes, la ruta
 * en el orden en que realmente se lee. Es código puro (sin Android).
 */
object ReadingOrder {

    data class Entry(val seq: Int, val record: MeterRecord, val at: Long)

    /** Momento en que se tomó la lectura; Long.MAX_VALUE si no se sabe (va al final). */
    fun timeOf(r: MeterRecord): Long = r.firstReadAt ?: r.readAt ?: Long.MAX_VALUE

    /** Lecturas tomadas con la app, numeradas desde 1 en el orden en que se tomaron. */
    fun build(records: List<MeterRecord>): List<Entry> =
        records.filter { it.appReading != null }
            .sortedWith(compareBy<MeterRecord> { timeOf(it) }.thenBy { it.row })
            .mapIndexed { i, r -> Entry(i + 1, r, timeOf(r)) }

    /** Fila del Excel -> número de orden de lectura. */
    fun sequence(records: List<MeterRecord>): Map<Int, Int> = build(records).associate { it.record.row to it.seq }

    /** Agrupa por día (en orden). Los que no tienen hora conocida van en un grupo aparte al final. */
    fun byDay(entries: List<Entry>): List<Pair<Long?, List<Entry>>> {
        val out = LinkedHashMap<String, Pair<Long?, MutableList<Entry>>>()
        for (e in entries) {
            val known = e.at != Long.MAX_VALUE
            val key = if (known) TimeUtil.dayKey(e.at) else "?"
            out.getOrPut(key) { (if (known) e.at else null) to mutableListOf() }.second.add(e)
        }
        return out.values.map { it.first to it.second }
    }

    /** Documento de texto con el orden de lectura, por días y con las columnas alineadas. */
    fun toText(routeName: String, period: String, records: List<MeterRecord>, now: Long): String {
        val entries = build(records)
        val days = byDay(entries)
        val sb = StringBuilder()
        fun line(t: String = "") { sb.append(t).append("\r\n") }
        fun fit(t: String, n: Int): String {
            val c = t.replace('\t', ' ').replace('\n', ' ').trim()
            return if (c.length <= n) c else c.take(n - 1) + "…"
        }
        val w = 100
        line("═".repeat(w))
        line("  ORDEN DE LECTURA")
        line("  Ruta: $routeName" + if (period.isNotBlank()) "   (periodo $period)" else "")
        line("  Generado: ${TimeUtil.dateTime(now)}")
        line("═".repeat(w))
        line()
        line("  ${entries.size} lecturas en ${days.size} ${if (days.size == 1) "día" else "días"} · faltan ${records.count { it.reading == null }} por leer")
        if (entries.isEmpty()) {
            line()
            line("  (todavía no hay lecturas tomadas con la app)")
            return sb.toString()
        }
        val sep = " │ "
        for ((dayStart, list) in days) {
            line()
            val title = dayStart?.let { TimeUtil.dateLong(it).replaceFirstChar { c -> c.uppercase() } } ?: "Sin fecha conocida"
            line(title.uppercase() + "  ·  ${list.size} lecturas" +
                if (dayStart != null) "  ·  de ${TimeUtil.hour(list.first().at)} a ${TimeUtil.hour(list.last().at)}" else "")
            line("─".repeat(w))
            line("Orden".padStart(5) + sep + "Hora".padEnd(9) + sep + "NIR".padStart(6) + sep + "Medidor".padEnd(12) + sep +
                "Usuario".padEnd(24) + sep + "Dirección".padEnd(22) + sep + "Lectura".padStart(8))
            line("─".repeat(w))
            for ((i, e) in list.withIndex()) {
                if (i > 0 && i % 10 == 0) line("·".repeat(w))
                val r = e.record
                line(e.seq.toString().padStart(5) + sep +
                    (if (e.at != Long.MAX_VALUE) TimeUtil.hour(e.at) else "").padEnd(9) + sep +
                    fit(r.nir, 6).padStart(6) + sep + fit(r.displayMeter, 12).padEnd(12) + sep +
                    fit(r.user, 24).padEnd(24) + sep + fit(r.address, 22).padEnd(22) + sep +
                    r.appReading.toString().padStart(8))
            }
        }
        line()
        line("─".repeat(w))
        line("Total: ${entries.size} lecturas · ruta $routeName")
        return sb.toString()
    }
}
