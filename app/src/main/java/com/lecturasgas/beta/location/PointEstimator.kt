package com.lecturasgas.beta.location

import com.lecturasgas.beta.util.Geo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Una medición del GPS de un medidor (cada visita guarda una). */
data class LocSample(val lat: Double, val lon: Double, val accuracy: Float, val time: Long)

/**
 * Calcula el punto de un medidor a partir de TODAS sus mediciones (no solo la última mezcla).
 *
 *  - Quita las mediciones que se alejan del grupo (rebotes, medidas tomadas desde otro lado).
 *  - Promedia las que quedan dándole más peso a las más precisas.
 *  - La precisión que declara es HONESTA: nunca baja de la dispersión real entre visitas ni
 *    del 60 % de la mejor medición, porque los errores del mismo sitio (paredes, árboles) se
 *    repiten en cada visita y no se quitan promediando.
 *
 * Es matemática pura (sin Android).
 */
object PointEstimator {
    const val MIN_ACCURACY = 3f
    private const val OUTLIER_MIN_M = 15.0
    private const val OUTLIER_FACTOR = 2.5
    private const val SYSTEMATIC_SHARE = 0.6

    fun estimate(samples: List<LocSample>): Spot? {
        if (samples.isEmpty()) return null
        if (samples.size == 1) {
            val s = samples[0]
            return Spot(s.lat, s.lon, max(s.accuracy, MIN_ACCURACY))
        }
        // Centro robusto (mediana): un punto muy lejano no lo arrastra.
        val medLat = median(samples.map { it.lat })
        val medLon = median(samples.map { it.lon })
        var kept = samples.filter { near(medLat, medLon, it) }
        if (kept.isEmpty()) {
            // Dos grupos separados: gana el de la medición más precisa.
            val best = samples.minByOrNull { accOf(it) }!!
            kept = samples.filter { near(best.lat, best.lon, it) }
        }
        repeat(2) {
            val c = center(kept)
            val next = kept.filter { near(c.first, c.second, it) }
            if (next.isNotEmpty() && next.size != kept.size) kept = next
        }
        val c = center(kept)
        var sumW = 0.0
        var sumSq = 0.0
        for (p in kept) {
            sumW += 1.0 / sq(accOf(p))
            val d = Geo.distanceMeters(c.first, c.second, p.lat, p.lon)
            sumSq += d * d
        }
        val sigma = sqrt(1.0 / sumW)
        val spread = if (kept.size >= 2) sqrt(sumSq / kept.size) else 0.0
        val best = kept.minOf { accOf(it) }
        val acc = max(MIN_ACCURACY.toDouble(), max(SYSTEMATIC_SHARE * best, max(spread, sigma)))
        return Spot(c.first, c.second, min(acc, kept.maxOf { accOf(it) }.toDouble()).toFloat())
    }

    private fun near(lat: Double, lon: Double, s: LocSample): Boolean =
        Geo.distanceMeters(lat, lon, s.lat, s.lon) <= max(OUTLIER_MIN_M, OUTLIER_FACTOR * accOf(s))

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun accOf(s: LocSample): Double = max(s.accuracy, MIN_ACCURACY).toDouble()
    private fun sq(v: Double) = v * v

    private fun center(list: List<LocSample>): Pair<Double, Double> {
        var sw = 0.0
        var la = 0.0
        var lo = 0.0
        for (p in list) {
            val w = 1.0 / sq(accOf(p))
            sw += w; la += p.lat * w; lo += p.lon * w
        }
        return la / sw to lo / sw
    }
}
