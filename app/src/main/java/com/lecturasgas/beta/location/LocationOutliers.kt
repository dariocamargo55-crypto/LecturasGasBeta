package com.lecturasgas.beta.location

import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.util.Geo

/** Un medidor cuyo punto GPS parece estar mal: cae lejos de los demás de su barrio. */
data class Suspect(val record: MeterRecord, val meters: Double, val reference: String)

/**
 * Detecta puntos "perdidos": un medidor de Villa Paola que aparece en otro barrio, a cuadras
 * de donde pasa la ruta. Se compara cada punto con el centro de su barrio (mediana, que no se
 * deja arrastrar por los puntos malos). Los puestos a mano ("manual") no se tocan.
 */
object LocationOutliers {
    private const val MIN_GROUP = 5
    private const val MIN_RADIUS_M = 250.0
    private const val FACTOR = 4.0
    private const val GLOBAL_MIN_RADIUS_M = 1_000.0
    private const val NEIGHBORS = 3
    private const val NEIGHBORS_MIN = 4
    private const val NEIGHBOR_CLUSTER_M = 150.0
    private const val NEIGHBOR_MIN_M = 80.0

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private data class Center(val lat: Double, val lon: Double, val typical: Double)

    private fun center(list: List<MeterRecord>): Center {
        val lat = median(list.map { it.lat!! })
        val lon = median(list.map { it.lon!! })
        val typical = median(list.map { Geo.distanceMeters(lat, lon, it.lat!!, it.lon!!) })
        return Center(lat, lon, typical)
    }

    fun find(records: List<MeterRecord>): List<Suspect> {
        val located = records.filter { it.lat != null && it.lon != null }
        if (located.size < MIN_GROUP) return emptyList()
        val byRow = LinkedHashMap<Int, Suspect>()
        for (s in byBarrio(located)) byRow[s.record.row] = s
        // Lo de los vecinos ve errores más chicos (60–250 m); si el mismo medidor sale en las dos revisiones, gana la mayor distancia.
        for (s in byNeighbors(located)) {
            val prev = byRow[s.record.row]
            if (prev == null || s.meters > prev.meters) byRow[s.record.row] = s
        }
        return byRow.values.sortedByDescending { it.meters }
    }

    /** Lejos del centro de su barrio (con el centro de cada barrio calculado UNA sola vez). */
    private fun byBarrio(located: List<MeterRecord>): List<Suspect> {
        val global = center(located)
        val groups = located.groupBy { it.barrio.trim().uppercase() }
        val centers = HashMap<String, Center>()
        for ((k, g) in groups) if (k.isNotEmpty() && g.size >= MIN_GROUP) centers[k] = center(g)
        val out = ArrayList<Suspect>()
        for (r in located) {
            if (r.locationSource == "manual") continue
            val key = r.barrio.trim().uppercase()
            val c: Center
            val limit: Double
            val ref: String
            val bc = centers[key]
            if (bc != null) {
                c = bc; limit = maxOf(MIN_RADIUS_M, FACTOR * c.typical); ref = "el barrio $key"
            } else {
                c = global; limit = maxOf(GLOBAL_MIN_RADIUS_M, FACTOR * c.typical); ref = "el resto de la ruta"
            }
            val d = Geo.distanceMeters(c.lat, c.lon, r.lat!!, r.lon!!)
            if (d > limit) out += Suspect(r, d, ref)
        }
        return out
    }

    /**
     * Lejos de sus vecinos en el orden de la ruta: los 3 medidores de antes y los 3 de después
     * (con GPS) están juntos entre sí, pero este queda a cuadras de todos ellos.
     */
    private fun byNeighbors(located: List<MeterRecord>): List<Suspect> {
        val out = ArrayList<Suspect>()
        val n = located.size
        for (i in 0 until n) {
            val r = located[i]
            if (r.locationSource == "manual") continue
            val near = ArrayList<MeterRecord>(6)
            for (j in (i - NEIGHBORS)..(i + NEIGHBORS)) {
                if (j == i || j < 0 || j >= n) continue
                val o = located[j]
                if (o.meterKey != r.meterKey) near.add(o)
            }
            if (near.size < NEIGHBORS_MIN) continue
            val cLat = median(near.map { it.lat!! })
            val cLon = median(near.map { it.lon!! })
            val spread = median(near.map { Geo.distanceMeters(cLat, cLon, it.lat!!, it.lon!!) })
            if (spread > NEIGHBOR_CLUSTER_M) continue            // los vecinos tampoco están juntos: no se puede opinar
            val d = Geo.distanceMeters(cLat, cLon, r.lat!!, r.lon!!)
            val nearest = near.minOf { Geo.distanceMeters(it.lat!!, it.lon!!, r.lat, r.lon) }
            if (d > maxOf(NEIGHBOR_MIN_M, 3.0 * spread + 20.0) && nearest > NEIGHBOR_MIN_M) {
                out += Suspect(r, nearest, "sus vecinos en el orden de la ruta")
            }
        }
        return out
    }
}
