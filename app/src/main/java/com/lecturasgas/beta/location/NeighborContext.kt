package com.lecturasgas.beta.location

import com.lecturasgas.beta.util.Geo

/**
 * Usa a los "vecinos" del medidor (los que van antes y después en el orden de la ruta y las
 * últimas entregas hechas) para decidir si una medición de GPS tiene sentido.
 *
 * Idea: las lecturas y entregas se hacen más o menos en orden, por barrio. Si estás parado en
 * el medidor y tu posición queda junto a sus vecinos, pero el punto guardado queda lejos de
 * todos ellos, el punto guardado es el que está mal.
 *
 * Es matemática pura (sin Android).
 */
object NeighborContext {

    enum class Verdict {
        /** No hay suficientes vecinos con ubicación para opinar. */
        UNKNOWN,
        /** La medición nueva cuadra con los vecinos y el punto guardado NO. */
        MEASURE_FITS,
        /** La medición nueva queda lejos de todos los vecinos: no es de fiar (¿entregaste en otro lado?). */
        MEASURE_OFF
    }

    /** Un vecino "apoya" un punto si está a esta distancia o menos. */
    const val NEAR_M = 150.0
    /** Mínimo de vecinos con ubicación para poder opinar. */
    const val MIN_ANCHORS = 2

    fun verdict(measure: Spot, stored: Spot?, anchors: List<Spot>): Verdict {
        if (anchors.size < MIN_ANCHORS) return Verdict.UNKNOWN
        val measureSupport = anchors.count { Geo.distanceMeters(measure.lat, measure.lon, it.lat, it.lon) <= NEAR_M }
        if (measureSupport == 0) return Verdict.MEASURE_OFF
        if (stored == null) return Verdict.UNKNOWN
        val storedSupport = anchors.count { Geo.distanceMeters(stored.lat, stored.lon, it.lat, it.lon) <= NEAR_M }
        return if (storedSupport == 0) Verdict.MEASURE_FITS else Verdict.UNKNOWN
    }
}
