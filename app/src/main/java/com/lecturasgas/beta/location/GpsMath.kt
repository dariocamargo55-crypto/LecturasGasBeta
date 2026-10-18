package com.lecturasgas.beta.location

import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.util.Geo
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Matemática del GPS (sin Android: se puede probar en el computador).
 *
 * Dos herramientas, las mismas ideas que usan Google Maps y Waze:
 *
 *  1) GpsSmoother  -> filtro de Kalman. Mezcla cada lectura nueva con la posición
 *     que ya tenías, dándole más peso a las lecturas más precisas. Resultado: el
 *     punto azul no "salta" y los saltos imposibles (rebotes entre edificios) se descartan.
 *
 *  2) PositionAverager -> promedia varias lecturas tomadas estando quieto frente al
 *     medidor, descartando las raras. Es lo que se guarda como ubicación del medidor.
 */

/** Posición ya filtrada. [accuracy] es el error estimado en metros. */
data class Estimate(val lat: Double, val lon: Double, val accuracy: Float)

/** Resultado de promediar lecturas: la ubicación final y con cuántas lecturas se hizo. */
data class Capture(val fix: GeoFix, val samples: Int)

class GpsSmoother {

    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1.0          // m² (incertidumbre actual)
    private var lastNs = 0L
    private var rejected = 0

    fun reset() {
        variance = -1.0
        rejected = 0
    }

    /**
     * @param accuracy error que declara el GPS (m)
     * @param speed velocidad en m/s si se conoce
     * @param timeNs reloj monótono de la lectura (elapsedRealtimeNanos)
     * @return la posición filtrada, o null si la lectura se descartó (repetida o salto imposible)
     */
    fun update(mLat: Double, mLon: Double, accuracy: Float, speed: Float?, timeNs: Long): Estimate? {
        val acc = max(accuracy.toDouble(), MIN_ACCURACY)

        if (variance < 0.0) return start(mLat, mLon, acc, timeNs)

        val dt = (timeNs - lastNs) / 1_000_000_000.0
        if (dt < MIN_DT) return null        // lectura repetida (GPS + Google entregan la misma)
        if (dt <= 0.0) return null

        // 1) Predicción: quieto = poca incertidumbre nueva; caminando/manejando = más.
        val q = when {
            speed == null -> 2.0
            speed < 0.4f -> 0.4
            else -> max(1.0, speed * 1.5)
        }
        val predicted = variance + dt * q * q

        // 2) Filtro de saltos: una lectura muy lejos de lo esperado se ignora (máx. 3 seguidas;
        //    si insiste, es que de verdad te moviste y se reinicia desde ahí).
        val dist = Geo.distanceMeters(lat, lon, mLat, mLon)
        val gate = max(GATE_MIN_M, 3.0 * sqrt(predicted + acc * acc))
        if (dist > gate) {
            rejected++
            if (rejected <= MAX_REJECTED) return null
            return start(mLat, mLon, acc, timeNs)
        }
        rejected = 0

        // 3) Corrección: cuanto más precisa la lectura nueva, más pesa.
        val k = predicted / (predicted + acc * acc)
        lat += k * (mLat - lat)
        lon += k * (mLon - lon)
        variance = max((1.0 - k) * predicted, MIN_VARIANCE)
        lastNs = timeNs
        return Estimate(lat, lon, sqrt(variance).toFloat())
    }

    private fun start(mLat: Double, mLon: Double, acc: Double, timeNs: Long): Estimate {
        lat = mLat
        lon = mLon
        variance = acc * acc
        lastNs = timeNs
        rejected = 0
        return Estimate(lat, lon, acc.toFloat())
    }

    private companion object {
        const val MIN_ACCURACY = 3.0      // ningún celular es mejor que esto de forma confiable
        const val MIN_VARIANCE = 9.0      // 3 m
        const val MIN_DT = 0.35           // s
        const val GATE_MIN_M = 25.0
        const val MAX_REJECTED = 3
    }
}

object PositionAverager {

    /** Lecturas peores que esto no se usan para guardar un medidor. */
    private const val MAX_SAMPLE_ACCURACY = 30f

    private class Point(val lat: Double, val lon: Double, val acc: Double)

    /**
     * Promedio ponderado (más peso a las lecturas más precisas) quitando las que quedan
     * lejos del grupo. La precisión que devuelve es la de la MEJOR lectura usada: no
     * prometemos más de lo que el celular realmente midió.
     */
    fun average(samples: List<GeoFix>): Capture? {
        var used = samples
            .filter { (it.accuracy ?: 999f) <= MAX_SAMPLE_ACCURACY }
            .map { Point(it.lat, it.lon, max((it.accuracy ?: 30f).toDouble(), 3.0)) }
        if (used.isEmpty()) return null

        repeat(2) {
            val c = weighted(used)
            val kept = used.filter {
                Geo.distanceMeters(c.lat, c.lon, it.lat, it.lon) <= max(10.0, 2.0 * it.acc)
            }
            if (kept.isNotEmpty() && kept.size != used.size) used = kept
        }
        val c = weighted(used)
        val best = used.minOf { it.acc }.toFloat()
        val last = samples.last()
        return Capture(
            GeoFix(c.lat, c.lon, best, last.time, null, null, "promedio"),
            used.size
        )
    }

    private fun weighted(list: List<Point>): Point {
        var sw = 0.0
        var sLat = 0.0
        var sLon = 0.0
        for (p in list) {
            val w = 1.0 / (p.acc * p.acc)
            sw += w
            sLat += p.lat * w
            sLon += p.lon * w
        }
        return Point(sLat / sw, sLon / sw, 0.0)
    }
}
