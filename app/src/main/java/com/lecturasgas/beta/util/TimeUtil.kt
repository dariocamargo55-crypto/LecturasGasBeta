package com.lecturasgas.beta.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    private val es = Locale("es", "CO")

    fun hour(millis: Long): String = SimpleDateFormat("h:mm a", es).format(Date(millis))
    fun dateTime(millis: Long): String = SimpleDateFormat("d MMM yyyy, h:mm a", es).format(Date(millis))
    /** "17" */
    fun dayNumber(millis: Long): String = SimpleDateFormat("d", es).format(Date(millis))
    /** "sáb" */
    fun weekdayShort(millis: Long): String = SimpleDateFormat("EEE", es).format(Date(millis)).trimEnd('.').lowercase(es)
    /** "oct" */
    fun monthShort(millis: Long): String = SimpleDateFormat("MMM", es).format(Date(millis)).trimEnd('.').lowercase(es)
    /** "Octubre 2026" */
    fun monthYear(millis: Long): String =
        SimpleDateFormat("MMMM yyyy", es).format(Date(millis)).replaceFirstChar { it.uppercase(es) }
    /** "sábado 17 de octubre de 2026" */
    fun dateLong(millis: Long): String = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", es).format(Date(millis))
    /** Un valor distinto por día (para contar días trabajados). */
    fun dayKey(millis: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(millis))
    fun fileStamp(millis: Long): String = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(millis))

    /** "hace un momento", "hace 5 min" o la hora si fue hace más de una hora. */
    fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ((now - millis) / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 1 -> "hace un momento"
            minutes < 60 -> "hace $minutes min"
            else -> hour(millis)
        }
    }

    fun duration(millis: Long): String {
        val minutes = (millis / 60_000L).coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h} h ${m} min" else "$m min"
    }
}
