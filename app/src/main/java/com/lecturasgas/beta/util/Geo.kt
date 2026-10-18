package com.lecturasgas.beta.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Distancia en metros entre dos coordenadas (fórmula de Haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Rumbo en grados (0 = norte, 90 = este) desde el punto 1 hacia el 2. */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun formatDistance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m"
        else String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)

    /** Flecha aproximada hacia un rumbo, útil para "¿hacia dónde queda?". */
    fun arrowFor(bearing: Double): String {
        val arrows = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        return arrows[(((bearing + 22.5) % 360) / 45).toInt().coerceIn(0, 7)]
    }
}
