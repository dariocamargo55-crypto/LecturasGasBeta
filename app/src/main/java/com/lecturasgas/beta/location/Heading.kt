package com.lecturasgas.beta.location

import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.util.Geo
import kotlin.math.abs

/**
 * Hacia dónde estás avanzando (0° = norte, 90° = este). Se usa para girar el mapa como
 * Google Maps / Waze. Solo se actualiza cuando de verdad te estás moviendo: parado, el GPS
 * da un rumbo inventado y el mapa daría vueltas.
 */
object Heading {
    /** Por debajo de esta velocidad (m/s) no se confía en el rumbo del GPS (≈ 3.6 km/h). */
    const val MIN_SPEED = 1.0f
    /** Si no hay rumbo del GPS, se calcula entre dos puntos que estén al menos así de separados. */
    const val MIN_STEP_M = 4.0
    /** Suavizado: 0 = no se mueve, 1 = sigue cada cambio sin filtrar. */
    const val SMOOTHING = 0.35
    /** El mapa solo gira si el cambio es de al menos estos grados (evita temblor). */
    const val MIN_TURN = 4.0

    /** Diferencia con signo más corta entre dos ángulos, en (-180, 180]. */
    fun delta(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    fun normalize(a: Double): Double = ((a % 360.0) + 360.0) % 360.0

    /** Rumbo medido en este fix, o null si estás quieto / no es confiable. */
    fun measured(prev: GeoFix?, now: GeoFix): Double? {
        val acc = now.accuracy
        if (acc != null && acc > 35f) return null
        val speed = now.speed
        val b = now.bearing
        if (b != null && speed != null && speed >= MIN_SPEED) return normalize(b.toDouble())
        if (prev != null) {
            val d = Geo.distanceMeters(prev.lat, prev.lon, now.lat, now.lon)
            if (d >= MIN_STEP_M) return normalize(Geo.bearingDegrees(prev.lat, prev.lon, now.lat, now.lon))
        }
        return null
    }

    /**
     * Nuevo rumbo suavizado a partir del anterior ([current]) y el fix nuevo.
     * Si no hay un rumbo confiable, se queda el que ya había.
     */
    fun next(current: Double?, prev: GeoFix?, now: GeoFix): Double? {
        val m = measured(prev, now) ?: return current
        if (current == null) return m
        return normalize(current + delta(current, m) * SMOOTHING)
    }

    /** ¿Vale la pena girar el mapa del rumbo mostrado [shown] al nuevo [target]? */
    fun shouldTurn(shown: Double?, target: Double): Boolean = shown == null || abs(delta(shown, target)) >= MIN_TURN
}
