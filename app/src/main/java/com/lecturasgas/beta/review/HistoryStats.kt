package com.lecturasgas.beta.review

import com.lecturasgas.beta.data.model.SessionSummary
import com.lecturasgas.beta.util.TimeUtil

/** Cuentas del historial de jornadas (pura, sin Android). */
object HistoryStats {

    data class Totals(
        val sessions: Int,
        val deliveries: Int,
        val meters: Double,
        /** Tiempo de las jornadas ya terminadas (las que siguen en curso no suman). */
        val millis: Long,
        /** Días distintos en que hubo jornada. */
        val days: Int
    )

    fun totals(list: List<SessionSummary>): Totals = Totals(
        sessions = list.size,
        deliveries = list.sumOf { it.deliveries },
        meters = list.sumOf { it.session.distanceMeters },
        millis = list.sumOf { s -> s.session.endedAt?.let { (it - s.session.startedAt).coerceAtLeast(0L) } ?: 0L },
        days = list.map { TimeUtil.dayKey(it.session.startedAt) }.toSet().size
    )

    /** Entregas por hora; null si la jornada fue demasiado corta para que el dato diga algo. */
    fun perHour(deliveries: Int, millis: Long): Double? =
        if (millis < MIN_FOR_PACE || deliveries <= 0) null else deliveries * 3_600_000.0 / millis

    /** Agrupa por mes (de más reciente a más antiguo), conservando el orden recibido dentro de cada mes. */
    fun byMonth(list: List<SessionSummary>): List<Pair<String, List<SessionSummary>>> {
        val out = LinkedHashMap<String, MutableList<SessionSummary>>()
        for (s in list) out.getOrPut(TimeUtil.monthYear(s.session.startedAt)) { mutableListOf() }.add(s)
        return out.map { it.key to it.value }
    }

    private const val MIN_FOR_PACE = 10 * 60_000L
}
