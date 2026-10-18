package com.lecturasgas.beta.delivery

import com.lecturasgas.beta.data.model.Delivery

/**
 * Notas de "dónde se dejó la factura" (primera puerta, puerta blanca, parte de atrás…).
 * Se recuerda la última nota de cada medidor (para la próxima vez) y las más usadas
 * (para ofrecerlas como botones rápidos).
 */
data class DeliveryNotes(
    /** meterKey -> última nota que se dejó en ese medidor. */
    val byMeter: Map<String, String> = emptyMap(),
    /** Todas las notas escritas, de la más antigua a la más reciente. */
    val history: List<String> = emptyList()
) {
    /** Botones rápidos: primero tus notas más usadas, completando con las típicas. Máximo [max]. */
    fun suggestions(max: Int = 8): List<String> {
        val used = history.groupingBy { it.lowercase() }.eachCount()
        val display = LinkedHashMap<String, String>()   // minúscula -> como se escribió la última vez
        for (n in history) display[n.lowercase()] = n
        val top = used.entries.sortedByDescending { it.value }.map { display[it.key] ?: it.key }
        val out = LinkedHashSet<String>()
        top.forEach { if (out.size < max) out.add(it) }
        for (d in DEFAULTS) {
            if (out.size >= max) break
            if (out.none { it.equals(d, ignoreCase = true) }) out.add(d)
        }
        return out.toList()
    }

    companion object {
        val DEFAULTS = listOf(
            "Primera puerta", "Segunda puerta", "Puerta blanca", "Parte de atrás",
            "Con el vecino", "Debajo de la puerta", "En el contador", "Buzón"
        )

        fun clean(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(80)

        fun from(all: List<Delivery>): DeliveryNotes {
            val withNote = all.filter { it.note.isNotBlank() }.sortedBy { it.deliveredAt }
            val by = LinkedHashMap<String, String>()
            for (d in withNote) by[d.meterKey] = d.note
            return DeliveryNotes(by, withNote.map { it.note })
        }
    }
}
