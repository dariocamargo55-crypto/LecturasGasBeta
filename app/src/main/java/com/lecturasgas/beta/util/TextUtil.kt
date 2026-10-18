package com.lecturasgas.beta.util

import java.math.BigDecimal
import java.text.Normalizer

/**
 * Utilidades de texto usadas por la importación y la búsqueda.
 *
 * Las expresiones regulares se crean UNA sola vez (son caras de compilar);
 * antes se creaban dentro de bucles y eso hacía lenta la app.
 */
object TextUtil {

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val NUMERIC = Regex("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")

    /** "Dirección  N° 5" -> "direccion n 5" (sin tildes, minúsculas, sin símbolos). */
    fun normalize(value: String): String {
        if (value.isEmpty()) return ""
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace(NON_ALNUM, " ")
            .trim()
    }

    /** Clave estable de un medidor: solo letras y números en mayúscula. "me-0997 401" -> "ME0997401". */
    fun meterKey(value: String): String =
        buildString(value.length) {
            for (ch in value) {
                if (ch.isLetterOrDigit()) append(ch.uppercaseChar())
            }
        }

    fun digits(value: String): String = value.filter { it.isDigit() }

    /**
     * Excel guarda los números como "1.0" o "1.2345E10".
     * Esto los deja como el usuario los ve: "1" y "12345000000".
     */
    fun cleanNumber(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty() || !NUMERIC.matches(text)) return text
        return runCatching {
            BigDecimal(text).stripTrailingZeros().toPlainString()
        }.getOrDefault(text)
    }

    /** "1146.0" -> 1146 ; texto no numérico -> null. */
    fun parseWhole(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return runCatching { BigDecimal(text).toLong() }.getOrNull()
    }

    /** NIR "0842" -> 842 para ordenar numéricamente. */
    fun nirNumber(nir: String): Long? = digits(nir).take(18).toLongOrNull()
}
