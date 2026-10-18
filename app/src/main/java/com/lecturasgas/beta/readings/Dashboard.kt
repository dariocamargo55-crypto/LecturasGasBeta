package com.lecturasgas.beta.readings

import com.lecturasgas.beta.data.model.MeterRecord
import java.util.Calendar
import java.util.Locale

/**
 * Cuentas del tablero de avance. Es código "puro" (sin pantallas): se calcula a partir de la
 * lista de medidores, así se puede probar fácil y no frena la interfaz.
 */
data class BarrioStat(val name: String, val total: Int, val read: Int) {
    val pending: Int get() = total - read
    val fraction: Float get() = if (total == 0) 0f else read.toFloat() / total
}

data class DashboardStats(
    val total: Int,
    val read: Int,
    val pending: Int,
    /** Pendientes con un motivo anotado (casa cerrada, etc.). */
    val skipped: Int,
    val withoutGps: Int,
    val meterFixes: Int,
    /** Lecturas registradas en la app HOY. */
    val readToday: Int,
    val firstTodayAt: Long?,
    /** Ritmo de hoy (lecturas por hora). Null si todavía hay pocos datos. */
    val perHour: Double?,
    /** Minutos estimados para terminar los que faltan por leer. Null si no se puede estimar. */
    val etaMinutes: Long?,
    /** Lecturas menores que la anterior: conviene revisarlas. */
    val negatives: Int,
    /** Medidores nuevos de esta ruta que todavía no tienen lectura. */
    val newPending: Int = 0,
    /** Barrios con más pendientes primero (los terminados al final). */
    val barrios: List<BarrioStat>
) {
    val fraction: Float get() = if (total == 0) 0f else read.toFloat() / total
    val percent: Int get() = (fraction * 100).toInt()
}

object Dashboard {

    fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun compute(records: List<MeterRecord>, now: Long, dayStart: Long = startOfDay(now)): DashboardStats {
        val read = records.count { it.isRead }
        val skipped = records.count { it.isSkipped }
        val today = records.mapNotNull { r -> r.readAt?.takeIf { r.appReading != null && it >= dayStart } }.sorted()

        // Ritmo: lecturas entre la primera y la última de hoy. Con menos de 5 lecturas o de
        // 15 minutos de trabajo sería un número engañoso, así que no se muestra.
        var perHour: Double? = null
        if (today.size >= MIN_SAMPLES) {
            val spanH = (today.last() - today.first()) / 3_600_000.0
            if (spanH >= MIN_SPAN_H) perHour = (today.size - 1) / spanH
        }
        val toRead = records.count { !it.isRead && !it.isSkipped }
        val eta = if (perHour != null && perHour > 0.0 && toRead > 0) (toRead / perHour * 60).toLong() else null

        val barrios = records.groupBy { it.barrio.trim().uppercase().ifBlank { "SIN BARRIO" } }
            .map { (name, list) -> BarrioStat(name, list.size, list.count { it.isRead }) }
            .sortedWith(compareByDescending<BarrioStat> { it.pending }.thenBy { it.name })

        return DashboardStats(
            total = records.size,
            read = read,
            pending = records.size - read,
            skipped = skipped,
            withoutGps = records.count { !it.hasLocation },
            meterFixes = records.count { it.hasMeterFix },
            readToday = today.size,
            firstTodayAt = today.firstOrNull(),
            perHour = perHour,
            etaMinutes = eta,
            negatives = records.count { (it.consumption ?: 0L) < 0L },
            newPending = records.count { it.isNew && !it.isRead },
            barrios = barrios
        )
    }

    /** Texto listo para pegar en WhatsApp. */
    fun summaryText(routeName: String, s: DashboardStats, dateLabel: String): String = buildString {
        append("📋 ").append(routeName).append(" · ").append(dateLabel).append('\n')
        append("Avance: ").append(s.read).append(" de ").append(s.total)
            .append(" leídas (").append(s.percent).append("%)\n")
        append("Hoy: ").append(s.readToday).append(" lecturas")
        s.perHour?.let { append(" · ").append(String.format(Locale.US, "%.0f", it)).append(" por hora") }
        append('\n')
        append("Faltan: ").append(s.pending)
        if (s.skipped > 0) append(" (").append(s.skipped).append(" con motivo: no se pudieron leer)")
        append('\n')
        s.etaMinutes?.let { append("Tiempo estimado para terminar: ").append(formatMinutes(it)).append('\n') }
        val top = s.barrios.filter { it.pending > 0 }.take(5)
        if (top.isNotEmpty()) {
            append("\nMás pendientes por barrio:\n")
            top.forEach { append("• ").append(it.name).append(": ").append(it.pending).append('\n') }
        }
    }.trimEnd()

    fun formatMinutes(min: Long): String {
        val h = min / 60
        val m = min % 60
        return if (h > 0) "$h h $m min" else "$m min"
    }

    private const val MIN_SAMPLES = 5
    private const val MIN_SPAN_H = 0.25
}

/**
 * Revisa si una lectura se ve rara comparada con la lectura anterior y con lo que consumen
 * normalmente los demás medidores de la ruta.
 */
object ConsumptionCheck {
    private const val FALLBACK_HIGH = 150L
    private const val MIN_SAMPLES = 20

    /** Consumo típico (mediana) de la ruta. Null si hay pocos datos para opinar. */
    fun typical(records: List<MeterRecord>): Long? {
        val values = records.mapNotNull { it.consumption }.filter { it > 0 }.sorted()
        if (values.size < MIN_SAMPLES) return null
        return values[values.size / 2]
    }

    /** Desde qué consumo se avisa. Si no se conoce el típico, un valor fijo. */
    fun highThreshold(typical: Long?): Long =
        if (typical == null) FALLBACK_HIGH else maxOf(typical * 4, typical + 25)

    /** Mensaje de aviso, o null si la lectura se ve normal. */
    fun warning(value: Long, previous: Long?, typical: Long?): String? {
        previous ?: return null
        val c = value - previous
        return when {
            c < 0 -> "Es MENOR que la anterior ($previous). Revisa el número."
            c > highThreshold(typical) -> buildString {
                append("Consumo muy alto (+$c)")
                if (typical != null) append(" · lo normal en esta ruta es ≈ $typical")
                append(". ¿Seguro?")
            }
            else -> null
        }
    }
}
