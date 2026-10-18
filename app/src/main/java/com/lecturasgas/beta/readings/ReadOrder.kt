package com.lecturasgas.beta.readings

import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.util.Geo

/** Un medidor visitado, con su número de orden (1 = el primero que leíste ese día). */
data class OrderPoint(val row: Int, val lat: Double, val lon: Double, val n: Int, val time: Long)

/**
 * El recorrido que hiciste leyendo: los medidores de UN día en el orden real en que los
 * registraste. [dayStart] es el inicio de ese día. [withoutGps] son los que visitaste pero
 * no tienen ubicación guardada (no se pueden dibujar).
 */
data class ReadTrail(
    val dayStart: Long,
    val isToday: Boolean,
    val visited: Int,
    val withoutGps: Int,
    val points: List<OrderPoint>,
    val meters: Double,
    /** Visitados cuyo punto parece mal ubicado: no se dibujan para no enredar la línea. */
    val doubtful: Int = 0
)

object ReadOrder {

    /**
     * Elige el día de hoy; si hoy aún no hay lecturas, el último día en que sí hubo
     * (así el botón no sale vacío al empezar la mañana). Cuenta como visita una lectura
     * registrada o un medidor marcado "no se pudo leer".
     */
    /** Días (inicio de cada uno, el más reciente primero) en que hubo lecturas o motivos. */
    fun days(records: List<MeterRecord>): List<Long> =
        records.mapNotNull { r ->
            when {
                r.appReading != null -> r.readAt
                r.isSkipped -> r.skipAt
                else -> null
            }
        }.map { Dashboard.startOfDay(it) }.distinct().sortedDescending()

    fun trail(
        records: List<MeterRecord>,
        now: Long,
        todayStart: Long = Dashboard.startOfDay(now),
        /** Día pedido (inicio). Si es null: hoy, o el último día con lecturas. */
        day: Long? = null,
        /** Filas con punto dudoso: no se dibujan. */
        exclude: Set<Int> = emptySet()
    ): ReadTrail? {
        val events = records.mapNotNull { r ->
            val t = when {
                r.appReading != null -> r.readAt
                r.isSkipped -> r.skipAt
                else -> null
            }
            if (t == null) null else r to t
        }
        if (events.isEmpty()) return null
        val byDay = events.groupBy { Dashboard.startOfDay(it.second) }
        val chosen = when {
            day != null && day in byDay -> day
            day != null -> return null
            todayStart in byDay -> todayStart
            else -> byDay.keys.max()
        }
        val ordered = byDay.getValue(chosen).sortedBy { it.second }

        val points = ArrayList<OrderPoint>()
        var withoutGps = 0
        var doubtful = 0
        for ((r, t) in ordered) {
            val lat = r.lat
            val lon = r.lon
            if (lat == null || lon == null) { withoutGps++; continue }
            if (r.row in exclude) { doubtful++; continue }
            points.add(OrderPoint(r.row, lat, lon, ordered.indexOfFirst { it.first.row == r.row } + 1, t))
        }
        var meters = 0.0
        for (i in 1 until points.size) {
            meters += Geo.distanceMeters(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        }
        return ReadTrail(chosen, chosen == todayStart, ordered.size, withoutGps, points, meters, doubtful)
    }
}
