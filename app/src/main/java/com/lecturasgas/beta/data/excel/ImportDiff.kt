package com.lecturasgas.beta.data.excel

/** Un medidor de la ruta que ya tenías en la app (lo que se compara contra el Excel nuevo). */
data class OldMeter(
    val key: String,
    val nir: String,
    val user: String,
    val barrio: String,
    val isNew: Boolean,
    val prevNir: String?
)

/** Un medidor del Excel que se va a importar. */
data class NewMeter(val key: String, val nir: String, val barrio: String)

/** Marcas que quedan guardadas en cada medidor nuevo del Excel. */
data class MeterFlag(val isNew: Boolean, val prevNir: String?)

/** Qué cambió entre la ruta que tenías y el Excel nuevo. */
data class ImportDiff(
    /** Una marca por cada fila del Excel nuevo, en el mismo orden. */
    val flags: List<MeterFlag>,
    val newCount: Int,
    val renumbered: Int,
    val removed: List<OldMeter>,
    val newBarrios: List<String>,
    /** false cuando no había una ruta anterior con la que comparar. */
    val comparable: Boolean
)

/**
 * Compara el Excel nuevo con la ruta anterior, emparejando por NÚMERO DE MEDIDOR (nunca por NIR,
 * porque el NIR se corre cuando se agrega un medidor en medio). Es código puro, sin Android.
 */
object ImportDiffer {

    fun compute(old: List<OldMeter>?, new: List<NewMeter>, samePeriod: Boolean): ImportDiff {
        if (old == null) {
            return ImportDiff(
                flags = new.map { MeterFlag(false, null) },
                newCount = 0, renumbered = 0, removed = emptyList(), newBarrios = emptyList(),
                comparable = false
            )
        }
        val oldByKey = HashMap<String, OldMeter>(old.size * 2)
        for (o in old) if (o.key.isNotEmpty() && o.key !in oldByKey) oldByKey[o.key] = o

        var newCount = 0
        var renumbered = 0
        val flags = ArrayList<MeterFlag>(new.size)
        val seenKeys = HashSet<String>(new.size * 2)
        for (n in new) {
            seenKeys.add(n.key)
            val o = oldByKey[n.key]
            when {
                o == null -> { flags.add(MeterFlag(true, null)); newCount++ }
                o.nir != n.nir -> { flags.add(MeterFlag(if (samePeriod) o.isNew else false, o.nir)); renumbered++ }
                else -> flags.add(MeterFlag(if (samePeriod) o.isNew else false, if (samePeriod) o.prevNir else null))
            }
        }
        val removed = old.filter { it.key.isNotEmpty() && it.key !in seenKeys }
            .distinctBy { it.key }
        val oldBarrios = old.map { it.barrio.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        val newBarrios = new.map { it.barrio.trim().uppercase() }.filter { it.isNotEmpty() }
            .distinct().filter { it !in oldBarrios }
        return ImportDiff(flags, newCount, renumbered, removed, newBarrios, comparable = true)
    }
}
