package com.lecturasgas.beta.location

import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.util.Geo
import kotlin.math.sqrt

/** Semáforo del GPS: ¿ya puedo guardar el punto del medidor? */
enum class Light { RED, YELLOW, GREEN }

/** Estado del GPS en un momento dado. [reason] es la frase corta que se le muestra al usuario. */
data class GpsQuality(
    val light: Light,
    val accuracy: Float?,
    val reason: String,
    val time: Long
) {
    companion object {
        val NONE = GpsQuality(Light.RED, null, "Sin señal GPS", 0L)
    }
}

/**
 * Reglas del semáforo (matemática pura, sin Android):
 *  - ROJO: sin señal, señal vieja o error mayor a [RED_ACC] m.
 *  - AMARILLO: hay señal pero aún se estabiliza o te estás moviendo.
 *  - VERDE: error de [GREEN_ACC] m o menos, quieto, varias muestras seguidas que coinciden.
 *    También verde si el error es de hasta [OK_ACC] m pero las muestras son muy estables.
 */
object GpsQualityRules {
    const val RED_ACC = 40f
    const val GREEN_ACC = 12f
    const val OK_ACC = 20f
    const val WINDOW_MS = 6_000L
    const val STALE_MS = 6_000L
    const val MOVING_SPEED = 1.0f
    const val MIN_SAMPLES = 4
    const val STABLE_SAMPLES = 8
    const val MAX_SPREAD_M = 4.0
    const val VERY_STABLE_SPREAD_M = 3.0

    /**
     * @param latest la posición ya filtrada (la que se muestra en el mapa)
     * @param samples lecturas crudas aceptadas recientemente
     */
    fun assess(latest: GeoFix?, samples: List<GeoFix>, now: Long): GpsQuality {
        if (latest == null || now - latest.time > STALE_MS) {
            return GpsQuality(Light.RED, null, "Sin señal GPS", now)
        }
        val acc = latest.accuracy ?: 999f
        if (acc > RED_ACC) return GpsQuality(Light.RED, acc, "GPS flojo (±${acc.toInt()} m)", now)

        val speed = latest.speed ?: 0f
        if (speed >= MOVING_SPEED) return GpsQuality(Light.YELLOW, acc, "Quédate quieto un momento", now)

        val recent = samples.filter { now - it.time <= WINDOW_MS }
        if (recent.size < MIN_SAMPLES) return GpsQuality(Light.YELLOW, acc, "Estabilizando…", now)

        val spread = spread(recent)
        val green = (acc <= GREEN_ACC && spread <= MAX_SPREAD_M) ||
                (acc <= OK_ACC && recent.size >= STABLE_SAMPLES && spread <= VERY_STABLE_SPREAD_M)
        return if (green) GpsQuality(Light.GREEN, acc, "GPS listo (±${acc.toInt()} m)", now)
        else GpsQuality(Light.YELLOW, acc, "Estabilizando… (±${acc.toInt()} m)", now)
    }

    /** Qué tanto se dispersan las muestras (m): raíz del promedio de distancias al centro al cuadrado. */
    fun spread(samples: List<GeoFix>): Double {
        if (samples.size < 2) return 0.0
        val lat = samples.sumOf { it.lat } / samples.size
        val lon = samples.sumOf { it.lon } / samples.size
        var s = 0.0
        for (p in samples) {
            val d = Geo.distanceMeters(lat, lon, p.lat, p.lon)
            s += d * d
        }
        return sqrt(s / samples.size)
    }
}
