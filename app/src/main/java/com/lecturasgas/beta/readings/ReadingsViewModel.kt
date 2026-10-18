package com.lecturasgas.beta.readings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lecturasgas.beta.appContainer
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.SearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

enum class ReadingFilter(val label: String) {
    PENDING("Pendientes"), READ("Leídas"), SKIPPED("Con motivo"), ALL("Todas")
}

/** Un elemento de la lista: encabezado de tramo o medidor. */
sealed class ReadingItem {
    data class Header(val id: String, val title: String, val read: Int, val total: Int, val expanded: Boolean) : ReadingItem()
    data class Meter(val record: MeterRecord) : ReadingItem()
}

data class ReadingsUi(
    val hasRoute: Boolean = false,
    val total: Int = 0,
    val read: Int = 0,
    val items: List<ReadingItem> = emptyList(),
    val searching: Boolean = false,
    val resultCount: Int = 0,
    val withoutGps: Int = 0,
    /** La última lectura registrada en la app (para el recordatorio bajo el buscador). */
    val lastRead: MeterRecord? = null,
    val skippedCount: Int = 0
)

@OptIn(FlowPreview::class)
class ReadingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.appContainer.repository
    private val prefs = app.appContainer.prefs

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _field = MutableStateFlow(prefs.searchFields(SCOPE))
    val field: StateFlow<Set<SearchField>> = _field.asStateFlow()

    private val _numeric = MutableStateFlow(prefs.numericKeyboard(SCOPE))
    val numeric: StateFlow<Boolean> = _numeric.asStateFlow()

    private val _filter = MutableStateFlow(
        runCatching { ReadingFilter.valueOf(prefs.readingsFilter) }.getOrDefault(ReadingFilter.PENDING)
    )
    val filter: StateFlow<ReadingFilter> = _filter.asStateFlow()

    /** Tramos que el usuario abrió/cerró a mano (invierten el estado por defecto). */
    private val toggled = MutableStateFlow<Set<String>>(emptySet())

    /** Campo que tenía el buscador antes de ver un barrio desde el resumen (se restaura al limpiar). */
    private var fieldBeforeBarrio: Set<SearchField>? = null

    fun setQuery(v: String) {
        _query.value = v
        if (v.isEmpty()) fieldBeforeBarrio?.let { _field.value = it; fieldBeforeBarrio = null }
    }

    /** Muestra los medidores de un barrio sin dañar tu forma habitual de buscar (por medidor, etc.). */
    fun showBarrio(name: String) {
        if (fieldBeforeBarrio == null) fieldBeforeBarrio = _field.value
        _field.value = setOf(SearchField.BARRIO)
        _query.value = name
    }
    fun toggleField(f: SearchField) {
        val next = SearchField.toggle(_field.value, f)
        _field.value = next
        fieldBeforeBarrio = null
        prefs.setSearchFields(SCOPE, next)
    }
    fun setNumeric(v: Boolean) { _numeric.value = v; prefs.setNumericKeyboard(SCOPE, v) }
    fun setFilter(f: ReadingFilter) { _filter.value = f; prefs.readingsFilter = f.name }
    fun toggleGroup(id: String) { toggled.value = toggled.value.let { if (id in it) it - id else it + id } }

    private val debouncedQuery = _query.debounce { if (it.isEmpty()) 0L else 160L }

    val ui: StateFlow<ReadingsUi> =
        combine(repo.records, repo.search, debouncedQuery, _field, _filter) { records, engine, q, f, filter ->
            Inputs(records, engine, q, f, filter)
        }.combine(toggled) { input, tog -> build(input, tog) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingsUi())

    private data class Inputs(
        val records: List<MeterRecord>,
        val engine: com.lecturasgas.beta.data.repository.SearchEngine,
        val query: String,
        val field: Set<SearchField>,
        val filter: ReadingFilter
    )

    private fun build(i: Inputs, toggled: Set<String>): ReadingsUi {
        val records = i.records
        val read = records.count { it.isRead }
        val withoutGps = records.count { !it.hasLocation }
        val lastRead = records
            .filter { it.appReading != null && it.readAt != null }
            .maxByOrNull { it.readAt ?: 0L }
        val skipped = records.count { it.isSkipped }
        if (i.query.isNotBlank()) {
            val results = i.engine.search(i.query, i.field, records)
            return ReadingsUi(
                hasRoute = records.isNotEmpty(), total = records.size, read = read,
                items = results.map { ReadingItem.Meter(it) },
                searching = true, resultCount = results.size, withoutGps = withoutGps,
                lastRead = lastRead, skippedCount = skipped
            )
        }
        val items = ArrayList<ReadingItem>(records.size + 64)
        for ((index, group) in groups(records).withIndex()) {
            val id = "g$index"
            val groupRead = group.records.count { it.isRead }
            val visible = when (i.filter) {
                ReadingFilter.PENDING -> group.records.filter { !it.isRead }
                ReadingFilter.READ -> group.records.filter { it.isRead }
                ReadingFilter.SKIPPED -> group.records.filter { it.isSkipped }
                ReadingFilter.ALL -> group.records
            }
            if (visible.isEmpty()) continue
            val defaultExpanded = i.filter != ReadingFilter.ALL || groupRead < group.records.size
            val expanded = if (id in toggled) !defaultExpanded else defaultExpanded
            items.add(ReadingItem.Header(id, group.title, groupRead, group.records.size, expanded))
            if (expanded) visible.forEach { items.add(ReadingItem.Meter(it)) }
        }
        return ReadingsUi(
            hasRoute = records.isNotEmpty(), total = records.size, read = read,
            items = items, searching = false, resultCount = 0, withoutGps = withoutGps,
            lastRead = lastRead, skippedCount = skipped
        )
    }

    private class Group(val title: String, val records: List<MeterRecord>)

    /**
     * Tramos: medidores seguidos del mismo barrio (en el orden del Excel).
     * Si un tramo es muy largo se parte en bloques de 100 por NIR, para
     * poder cerrar los que ya terminaste y moverte rápido.
     */
    private fun groups(records: List<MeterRecord>): List<Group> {
        if (records.isEmpty()) return emptyList()
        val raw = mutableListOf<Pair<String, MutableList<MeterRecord>>>()
        for (r in records) {
            val name = r.barrio.trim().uppercase().ifBlank { "SIN BARRIO" }
            if (raw.isEmpty() || raw.last().first != name) raw.add(name to mutableListOf())
            raw.last().second.add(r)
        }
        val out = mutableListOf<Group>()
        for ((name, list) in raw) {
            if (list.size <= BLOCK) {
                out.add(Group("$name · NIR ${list.first().nir}–${list.last().nir}", list))
            } else {
                list.chunked(BLOCK).forEach { chunk ->
                    out.add(Group("$name · NIR ${chunk.first().nir}–${chunk.last().nir}", chunk))
                }
            }
        }
        return out
    }

    companion object {
        const val SCOPE = "readings"
        private const val BLOCK = 100
    }
}
