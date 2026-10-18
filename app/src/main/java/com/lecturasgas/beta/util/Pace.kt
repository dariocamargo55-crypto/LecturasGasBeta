package com.lecturasgas.beta.util

/** Ritmo de trabajo (acciones por hora) a partir de las horas en que se hicieron. */
object Pace {
    /**
     * Null si hay pocos datos: con menos de [minSamples] acciones o de 15 minutos de trabajo,
     * el número sería engañoso.
     */
    fun perHour(times: List<Long>, minSamples: Int = 4): Double? {
        if (times.size < minSamples) return null
        val sorted = times.sorted()
        val spanH = (sorted.last() - sorted.first()) / 3_600_000.0
        if (spanH < 0.25) return null
        return (sorted.size - 1) / spanH
    }

    /** Minutos que faltan para terminar [remaining] acciones a este ritmo. */
    fun etaMinutes(remaining: Int, perHour: Double?): Long? {
        if (perHour == null || perHour <= 0.0 || remaining <= 0) return null
        return (remaining / perHour * 60).toLong()
    }
}
