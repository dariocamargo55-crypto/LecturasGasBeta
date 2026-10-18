package com.lecturasgas.beta.data.repository

import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.data.model.MeterRecord
import kotlin.math.cos

/**
 * "Ruta aprendida".
 *
 * Idea: en cada jornada guardada, cada medidor quedó en una posición
 * (1º, 2º, 3º...). Esa posición se pasa a una escala de 0 a 1 y se promedia
 * entre jornadas. Así, si casi siempre entregas el NIR 003 antes que el 002,
 * la app lo sugiere en ese orden.
 *
 * Los medidores sin historial se ubican según su NIR.
 */
object RoutePlanner {

    /** Medidor -> posición promedio (0 = inicio de la ruta, 1 = final). */
    fun learnRanks(history: List<Delivery>): Map<String, Double> {
        val sums = HashMap<String, Double>()
        val counts = HashMap<String, Int>()
        for ((_, list) in history.groupBy { it.sessionId }) {
            if (list.size < 3) continue
            val ordered = list.sortedBy { it.order }
            val last = (ordered.size - 1).toDouble()
            ordered.forEachIndexed { i, d ->
                sums[d.meterKey] = (sums[d.meterKey] ?: 0.0) + i / last
                counts[d.meterKey] = (counts[d.meterKey] ?: 0) + 1
            }
        }
        return sums.mapValues { (k, v) -> v / (counts[k] ?: 1) }
    }

    /** Orden por NIR (numérico cuando se puede). */
    fun byNir(records: List<MeterRecord>): List<MeterRecord> =
        records.sortedWith(
            compareBy<MeterRecord> { it.nirNumber == null }
                .thenBy { it.nirNumber ?: Long.MAX_VALUE }
                .thenBy { it.nir }
                .thenBy { it.row }
        )

    /** Orden sugerido de toda la ruta: aprendido si hay historial, si no por NIR. */
    fun plan(records: List<MeterRecord>, learned: Map<String, Double>): List<MeterRecord> {
        val nirOrder = byNir(records)
        if (learned.isEmpty()) return nirOrder
        val last = (nirOrder.size - 1).coerceAtLeast(1).toDouble()
        val key = HashMap<Int, Double>(nirOrder.size)
        nirOrder.forEachIndexed { i, r ->
            key[r.row] = learned[r.meterKey] ?: (i / last)
        }
        return nirOrder.sortedBy { key[it.row] ?: 1.0 }
    }

    /**
     * Siguiente factura: la primera pendiente DESPUÉS de la última entregada
     * en el orden sugerido (para seguir avanzando aunque te hayas saltado
     * alguna). Si ya no hay más adelante, vuelve a las que quedaron atrás.
     */
    fun next(plan: List<MeterRecord>, delivered: Set<Int>, lastDeliveredRow: Int?): MeterRecord? {
        if (plan.isEmpty()) return null
        val from = if (lastDeliveredRow == null) -1 else plan.indexOfFirst { it.row == lastDeliveredRow }
        for (i in (from + 1) until plan.size) if (plan[i].row !in delivered) return plan[i]
        return plan.firstOrNull { it.row !in delivered }
    }

    /**
     * Tramo corto para dibujar en el mapa (la línea naranja).
     *
     * - Empieza en la siguiente factura ([start]).
     * - Con historial aprendido ([useLearned]) sigue el orden aprendido.
     * - Sin historial va "al más cercano": desde cada punto salta al pendiente
     *   con GPS que tenga más cerca. Así la línea no cruza todo el pueblo.
     * - Solo devuelve [count] puntos y corta si el siguiente queda a más de
     *   [maxLegMeters] (para no dibujar saltos largos).
     */
    fun shortPath(
        plan: List<MeterRecord>,
        delivered: Set<Int>,
        start: MeterRecord,
        useLearned: Boolean,
        count: Int,
        maxLegMeters: Double = 500.0
    ): List<MeterRecord> {
        val pending = plan.filter { it.row !in delivered && it.lat != null && it.lon != null }
        if (pending.isEmpty()) return emptyList()
        val first = if (start.lat != null && start.lon != null) start else pending.first()
        val path = ArrayList<MeterRecord>(count)
        path.add(first)

        if (useLearned) {
            val from = plan.indexOfFirst { it.row == first.row }.coerceAtLeast(-1)
            for (i in (from + 1) until plan.size) {
                val r = plan[i]
                if (r.row in delivered || r.lat == null || r.lon == null || r.row == first.row) continue
                path.add(r)
                if (path.size >= count) break
            }
            return path
        }

        val remaining = pending.filter { it.row != first.row }.toMutableList()
        var current = first
        while (path.size < count && remaining.isNotEmpty()) {
            var best = -1
            var bestD = Double.MAX_VALUE
            val cLat = current.lat ?: break
            val cLon = current.lon ?: break
            val k = cos(Math.toRadians(cLat))
            for (i in remaining.indices) {
                val r = remaining[i]
                val dy = ((r.lat ?: continue) - cLat) * 111_320.0
                val dx = ((r.lon ?: continue) - cLon) * 111_320.0 * k
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = i }
            }
            if (best < 0 || bestD > maxLegMeters * maxLegMeters) break
            current = remaining.removeAt(best)
            path.add(current)
        }
        return path
    }
}
