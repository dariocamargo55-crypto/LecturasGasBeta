package com.lecturasgas.beta.location

import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.util.Geo

/**
 * Un medidor que tienes cerca. [unread]: aún no tiene lectura (se ofrece "Leer").
 * [weak]: su GPS guardado es dudoso (se ofrece "Mejorar ubicación").
 * [canImprove]: tu GPS de ahora es lo bastante bueno para mejorar ese punto.
 */
data class Near(
    val record: MeterRecord,
    val meters: Double,
    val unread: Boolean,
    val weak: Boolean,
    val canImprove: Boolean
)

object Proximity {
    /** "Estás frente a…": a menos de esto del punto guardado. */
    const val NEAR_M = 25.0
    /** Si el punto guardado es dudoso puede estar corrido, así que se busca más lejos. */
    const val WEAK_NEAR_M = 45.0
    /** Con un GPS peor que esto no se avisa nada (sería adivinar). */
    const val MAX_FIX_ACCURACY = 30f
    /** Para mejorar una ubicación, el GPS de ahora debe tener este error o menos. */
    const val IMPROVE_FIX_ACCURACY = 15f
    private const val MAX_FIX_AGE_MS = 20_000L

    /**
     * El medidor más cercano que necesita algo: leerlo o mejorar su ubicación.
     * Los que ya descartaste con ✕ ([dismissed]) no vuelven a molestar.
     */
    fun nearest(
        fix: GeoFix?,
        records: List<MeterRecord>,
        dismissed: Set<Int>,
        now: Long = System.currentTimeMillis(),
        nearM: Double = NEAR_M,
        weakHint: Boolean = true
    ): Near? {
        fix ?: return null
        val acc = fix.accuracy ?: return null
        if (acc > MAX_FIX_ACCURACY || now - fix.time > MAX_FIX_AGE_MS) return null

        var best: MeterRecord? = null
        var bestD = Double.MAX_VALUE
        for (r in records) {
            val lat = r.lat ?: continue
            val lon = r.lon ?: continue
            if (r.row in dismissed) continue
            val unread = !r.isRead
            val weak = weakHint && r.hasWeakLocation
            if (!unread && !weak) continue
            val limit = if (weak) maxOf(WEAK_NEAR_M, nearM * 1.8) else nearM
            val d = Geo.distanceMeters(fix.lat, fix.lon, lat, lon)
            if (d <= limit && d < bestD) { bestD = d; best = r }
        }
        val r = best ?: return null
        return Near(r, bestD, unread = !r.isRead, weak = weakHint && r.hasWeakLocation, canImprove = acc <= IMPROVE_FIX_ACCURACY)
    }
}
