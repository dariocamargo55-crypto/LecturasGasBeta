package com.lecturasgas.beta.review

import com.lecturasgas.beta.data.model.MeterRecord

/**
 * Historial de UN medidor: lecturas de meses anteriores (las que la app guardó al cambiar de mes),
 * la lectura anterior que trae el Excel, la de este mes y los cambios que le hiciste.
 * Es código puro (sin Android).
 */
object MeterHistory {

    /** Una lectura de un periodo anterior, guardada por la app al importar el Excel del mes siguiente. */
    data class Snapshot(val period: String, val reading: Long, val previous: Long?, val takenAt: Long?, val fromApp: Boolean)

    /** Un cambio hecho en este mes (lectura nueva, corrección o borrado). */
    data class LogEntry(val at: Long, val value: Long?, val kind: String)

    data class Entry(
        val label: String,
        val reading: Long?,
        val consumption: Long?,
        val note: String
    )

    data class View(
        val entries: List<Entry>,
        val changes: List<LogEntry>,
        /** Consumo promedio de los periodos con dato, sin contar el de este mes. */
        val average: Long?,
        /** Este mes frente al promedio: «+35 %», «−10 %»… (null si no se puede). */
        val comparison: String?
    )

    fun build(record: MeterRecord, currentPeriod: String, snapshots: List<Snapshot>, log: List<LogEntry>): View {
        val entries = mutableListOf<Entry>()
        val current = record.reading
        val label = currentPeriod.ifBlank { "Este mes" }.let { if (it == "Este mes") it else "Este mes · $it" }
        entries.add(
            Entry(
                label, current, record.consumption,
                when {
                    record.appReading != null -> "Tomada con la app"
                    record.excelReading != null -> "Ya venía en el Excel"
                    record.isSkipped -> "No se pudo leer: ${record.skipReason}"
                    else -> "Sin leer"
                }
            )
        )

        // Los periodos guardados, del más reciente al más antiguo (el orden de guardado).
        val ordered = snapshots
        val newestSnapshot = ordered.firstOrNull()
        val prev = record.previous
        // La «lectura anterior» del Excel suele ser la del último periodo guardado: no se repite.
        val duplicatesPrevious = prev != null && newestSnapshot != null && newestSnapshot.reading == prev
        if (prev != null && !duplicatesPrevious) {
            entries.add(Entry("Mes anterior (según el Excel)", prev, null, "Columna de lectura anterior"))
        }
        for ((i, s) in ordered.withIndex()) {
            val c = s.previous?.let { s.reading - it }
            val note = (if (s.fromApp) "Tomada con la app" else "Venía en el Excel") +
                    if (i == 0 && duplicatesPrevious) " · es la lectura anterior de este mes" else ""
            entries.add(Entry(s.period.ifBlank { "Mes guardado" }, s.reading, c, note))
        }

        val past = entries.drop(1).mapNotNull { it.consumption }.filter { it >= 0 }
        val average = if (past.isEmpty()) null else past.average().toLong()
        val now = record.consumption
        val comparison = if (average != null && average > 0 && now != null && now >= 0) {
            val pct = ((now - average) * 100.0 / average).toInt()
            if (pct >= 0) "+$pct %" else "−${-pct} %"
        } else null

        return View(entries, log, average, comparison)
    }
}
