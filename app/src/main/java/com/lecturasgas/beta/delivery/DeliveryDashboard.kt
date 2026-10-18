package com.lecturasgas.beta.delivery

import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.readings.BarrioStat
import com.lecturasgas.beta.readings.Dashboard
import com.lecturasgas.beta.util.Pace
import java.util.Locale

/** Cuentas del resumen de entregas (código puro, fácil de probar). */
data class DeliveryStats(
    val total: Int,
    val delivered: Int,
    val pending: Int,
    val deliveredToday: Int,
    val firstTodayAt: Long?,
    val lastTodayAt: Long?,
    val perHour: Double?,
    val etaMinutes: Long?,
    /** Facturas que dejaste "para después". */
    val skipped: Int,
    /** Por entregar y sin punto GPS. */
    val pendingWithoutGps: Int,
    /** Entregadas sin ubicación guardada. */
    val deliveredWithoutGps: Int,
    /** Entregas de otros días (antes de hoy). */
    val deliveredBefore: Int,
    /** "read" = entregadas en cada barrio. Los de más pendientes primero. */
    val barrios: List<BarrioStat>
) {
    val fraction: Float get() = if (total == 0) 0f else delivered.toFloat() / total
    val percent: Int get() = (fraction * 100).toInt()
}

object DeliveryDashboard {

    fun compute(
        records: List<MeterRecord>,
        deliveries: List<Delivery>,
        skippedRows: Set<Int>,
        now: Long,
        dayStart: Long = Dashboard.startOfDay(now)
    ): DeliveryStats {
        val deliveredRows = deliveries.map { it.row }.toHashSet()
        val today = deliveries.map { it.deliveredAt }.filter { it >= dayStart }.sorted()
        // Los paquetes (buzón general) se registran de golpe: no cuentan para el ritmo por hora.
        val paceTimes = deliveries.filter { it.bulk == 0L }.map { it.deliveredAt }.filter { it >= dayStart }.sorted()
        val perHour = Pace.perHour(paceTimes)
        val pendingRecords = records.filter { it.row !in deliveredRows }
        val pending = pendingRecords.size
        val barrios = records.groupBy { it.barrio.trim().uppercase().ifBlank { "SIN BARRIO" } }
            .map { (name, list) -> BarrioStat(name, list.size, list.count { it.row in deliveredRows }) }
            .sortedWith(compareByDescending<BarrioStat> { it.pending }.thenBy { it.name })
        return DeliveryStats(
            total = records.size,
            delivered = records.size - pending,
            pending = pending,
            deliveredToday = today.size,
            firstTodayAt = today.firstOrNull(),
            lastTodayAt = today.lastOrNull(),
            perHour = perHour,
            etaMinutes = Pace.etaMinutes(pending, perHour),
            skipped = pendingRecords.count { it.row in skippedRows },
            pendingWithoutGps = pendingRecords.count { !it.hasLocation },
            deliveredWithoutGps = deliveries.count { it.lat == null },
            deliveredBefore = deliveries.count { it.deliveredAt < dayStart },
            barrios = barrios
        )
    }

    /** Texto listo para pegar en WhatsApp. */
    fun summaryText(
        routeName: String,
        s: DeliveryStats,
        dateLabel: String,
        distanceText: String?,
        durationText: String?
    ): String = buildString {
        append("📦 Entrega de facturas · ").append(routeName).append(" · ").append(dateLabel).append('\n')
        append("Avance: ").append(s.delivered).append(" de ").append(s.total)
            .append(" entregadas (").append(s.percent).append("%)\n")
        append("Hoy: ").append(s.deliveredToday).append(" entregas")
        s.perHour?.let { append(" · ").append(String.format(Locale.US, "%.0f", it)).append(" por hora") }
        append('\n')
        if (distanceText != null && durationText != null) {
            append("Recorrido: ").append(distanceText).append(" en ").append(durationText).append('\n')
        }
        append("Faltan: ").append(s.pending)
        if (s.skipped > 0) append(" (").append(s.skipped).append(" para después)")
        append('\n')
        s.etaMinutes?.let { append("Tiempo estimado para terminar: ").append(Dashboard.formatMinutes(it)).append('\n') }
        val top = s.barrios.filter { it.pending > 0 }.take(5)
        if (top.isNotEmpty()) {
            append("\nMás pendientes por barrio:\n")
            top.forEach { append("• ").append(it.name).append(": ").append(it.pending).append('\n') }
        }
    }.trimEnd()
}
