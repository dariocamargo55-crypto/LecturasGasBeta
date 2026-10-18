package com.lecturasgas.beta.data.repository

import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.SearchField
import com.lecturasgas.beta.util.TextUtil

/**
 * Búsqueda rápida. Los textos de cada medidor se normalizan UNA vez
 * (cuando se importa la ruta) y se guardan en un índice; buscar solo
 * compara textos ya preparados.
 */
class SearchEngine(records: List<MeterRecord>) {

    private class Entry(
        val row: Int,
        val meterKey: String,
        val nir: String,
        val nirDigits: String,
        val user: String,
        val address: String,
        val barrio: String,
        val observation: String
    )

    private val entries: Map<Int, Entry> = records.associate { r ->
        r.row to Entry(
            row = r.row,
            meterKey = r.meterKey,
            nir = TextUtil.normalize(r.nir),
            nirDigits = TextUtil.digits(r.nir).trimStart('0'),
            user = TextUtil.normalize(r.user),
            address = TextUtil.normalize(r.address),
            barrio = TextUtil.normalize(r.barrio),
            observation = TextUtil.normalize(r.observation)
        )
    }

    val size: Int get() = entries.size

    /** Devuelve los medidores que coinciden, los mejores primero. */
    fun search(query: String, field: SearchField, records: List<MeterRecord>, limit: Int = 300): List<MeterRecord> =
        search(query, setOf(field), records, limit)

    /** Busca en uno o varios campos a la vez: gana el mejor puntaje de los campos elegidos. */
    fun search(query: String, fields: Set<SearchField>, records: List<MeterRecord>, limit: Int = 300): List<MeterRecord> {
        val q = query.trim()
        if (q.isEmpty()) return records
        val qNorm = TextUtil.normalize(q)
        val qKey = TextUtil.meterKey(q)
        val qDigits = TextUtil.digits(q).trimStart('0')
        val words = qNorm.split(' ').filter { it.isNotEmpty() }

        val scored = ArrayList<Pair<MeterRecord, Int>>()
        for (r in records) {
            val e = entries[r.row] ?: continue
            var s = 0
            for (field in fields) {
                val fs = when (field) {
                    SearchField.METER -> meterScore(e, r, qKey)
                    SearchField.NIR -> nirScore(e, qNorm, qDigits)
                    SearchField.USER -> textScore(e.user, words)
                    SearchField.ADDRESS -> textScore(e.address, words)
                    SearchField.BARRIO -> textScore(e.barrio, words)
                    SearchField.OBSERVATION -> textScore(e.observation, words)
                    SearchField.READING -> readingScore(r, qDigits)
                    SearchField.ALL -> maxOf(
                        meterScore(e, r, qKey),
                        nirScore(e, qNorm, qDigits) - 5,
                        textScore(e.user, words),
                        textScore(e.address, words) - 2,
                        textScore(e.barrio, words) - 10,
                        textScore(e.observation, words) - 10,
                        readingScore(r, qDigits) - 20
                    )
                }
                if (fs > s) s = fs
            }
            if (s > 0) scored.add(r to s)
        }
        scored.sortWith(compareByDescending<Pair<MeterRecord, Int>> { it.second }.thenBy { it.first.row })
        return scored.take(limit).map { it.first }
    }

    /** Busca por el número del Excel Y por el número real corregido por el usuario. */
    private fun meterScore(e: Entry, r: MeterRecord, q: String): Int {
        if (q.isEmpty()) return 0
        val fixed = r.realMeterKey?.let { keyScore(it, q) } ?: 0
        return maxOf(keyScore(e.meterKey, q), fixed)
    }

    private fun keyScore(k: String, q: String): Int {
        return when {
            k == q -> 100
            k.endsWith(q) -> 90          // "últimos 4 dígitos"
            k.startsWith(q) -> 80
            k.contains(q) -> 60
            else -> 0
        }
    }

    private fun nirScore(e: Entry, q: String, qDigits: String): Int = when {
        q.isEmpty() -> 0
        qDigits.isNotEmpty() && e.nirDigits == qDigits -> 100
        e.nir == q -> 100
        e.nir.startsWith(q) -> 70
        e.nir.contains(q) -> 40
        else -> 0
    }

    private fun textScore(text: String, words: List<String>): Int {
        if (words.isEmpty() || text.isEmpty()) return 0
        var score = 0
        for (w in words) {
            score += when {
                text.startsWith(w) -> 30
                text.contains(" $w") -> 25
                text.contains(w) -> 15
                else -> return 0         // todas las palabras deben aparecer
            }
        }
        return 40 + score
    }

    private fun readingScore(r: MeterRecord, qDigits: String): Int {
        if (qDigits.isEmpty()) return 0
        val reading = r.reading?.toString() ?: ""
        val previous = r.previous?.toString() ?: ""
        return when {
            reading == qDigits -> 90
            previous == qDigits -> 80
            reading.startsWith(qDigits) -> 50
            previous.startsWith(qDigits) -> 40
            else -> 0
        }
    }
}
