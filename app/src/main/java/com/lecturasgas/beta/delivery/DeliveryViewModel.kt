package com.lecturasgas.beta.delivery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lecturasgas.beta.appContainer
import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.data.model.DeliverySession
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.SearchField
import com.lecturasgas.beta.data.repository.LecturasRepository
import com.lecturasgas.beta.data.repository.RoutePlanner
import com.lecturasgas.beta.location.DeliveryTrackingService
import com.lecturasgas.beta.location.LocationFusion
import com.lecturasgas.beta.location.Spot
import com.lecturasgas.beta.readings.Dashboard
import com.lecturasgas.beta.util.Pace
import com.lecturasgas.beta.util.Geo
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
import kotlinx.coroutines.launch

enum class DeliveryTab(val label: String) { PENDING("Por entregar"), DONE("Entregadas") }

/** Orden sugerido de la ruta (cambia solo cuando cambian los datos, no con cada GPS). */
data class DeliveryPlan(
    val plan: List<MeterRecord> = emptyList(),
    val deliveredRows: Set<Int> = emptySet(),
    val next: MeterRecord? = null,
    /** La que sigue después de la siguiente (para ir pensando el camino). */
    val after: MeterRecord? = null,
    val skipped: Set<Int> = emptySet(),
    val learned: Boolean = false
)

data class Nearby(val record: MeterRecord, val meters: Double)

data class DeliveryUi(
    val hasRoute: Boolean = false,
    val session: DeliverySession? = null,
    val total: Int = 0,
    val delivered: Int = 0,
    val deliveredToday: Int = 0,
    val next: MeterRecord? = null,
    val after: MeterRecord? = null,
    val afterDistance: Double? = null,
    /** Facturas que dejaste "para después" en esta jornada. */
    val skippedRows: Set<Int> = emptySet(),
    /** Entregas por hora de hoy y minutos estimados para terminar (null si aún hay pocos datos). */
    val perHour: Double? = null,
    val etaMinutes: Long? = null,
    /** Última entrega de hoy (para poder deshacerla con un toque si te equivocaste). */
    val lastDelivery: Delivery? = null,
    val nextDistance: Double? = null,
    val nextBearing: Double? = null,
    val nearby: Nearby? = null,
    val pending: List<MeterRecord> = emptyList(),
    val done: List<Delivery> = emptyList(),
    val searching: Boolean = false,
    val learned: Boolean = false,
    val stats: DeliveryStats? = null,
    val notes: DeliveryNotes = DeliveryNotes(),
    val noteSuggestions: List<String> = emptyList()
)

@OptIn(FlowPreview::class)
class DeliveryViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo = app.appContainer.repository
    private val prefs = app.appContainer.prefs
    private val tracker = app.appContainer.tracker

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _field = MutableStateFlow(prefs.searchFields(SCOPE))
    val field: StateFlow<Set<SearchField>> = _field.asStateFlow()
    private val _numeric = MutableStateFlow(prefs.numericKeyboard(SCOPE))
    val numeric: StateFlow<Boolean> = _numeric.asStateFlow()
    private val _tab = MutableStateFlow(DeliveryTab.PENDING)
    val tab: StateFlow<DeliveryTab> = _tab.asStateFlow()

    fun setQuery(v: String) {
        _query.value = v
        if (v.isEmpty()) fieldBeforeBarrio?.let { _field.value = it; fieldBeforeBarrio = null }
    }
    fun toggleField(f: SearchField) {
        val next = SearchField.toggle(_field.value, f)
        _field.value = next
        fieldBeforeBarrio = null
        prefs.setSearchFields(SCOPE, next)
    }
    fun setNumeric(v: Boolean) { _numeric.value = v; prefs.setNumericKeyboard(SCOPE, v) }
    fun setTab(t: DeliveryTab) { _tab.value = t }

    private var fieldBeforeBarrio: Set<SearchField>? = null

    /** Muestra solo un barrio sin cambiar tu forma habitual de buscar. */
    fun showBarrio(name: String) {
        if (fieldBeforeBarrio == null) fieldBeforeBarrio = _field.value
        _field.value = setOf(SearchField.BARRIO)
        _query.value = name
        _tab.value = DeliveryTab.PENDING
    }

    val track = repo.track
    val session = repo.session

    /** Plan: se recalcula cuando cambian medidores, entregas o la ruta aprendida. */
    val plan: StateFlow<DeliveryPlan> =
        combine(repo.records, repo.deliveries, repo.learned, repo.skippedForNow) { records, deliveries, learned, skipped ->
            val ordered = RoutePlanner.plan(records, learned)
            val delivered = deliveries.map { it.row }.toHashSet()
            val last = deliveries.maxByOrNull { it.order }?.row
            // Las "saltadas" se dejan para después; si solo quedan esas, se vuelven a ofrecer.
            val done = delivered + skipped
            val next = RoutePlanner.next(ordered, done, last) ?: RoutePlanner.next(ordered, delivered, last)
            val after = next?.let { RoutePlanner.next(ordered, done + it.row, it.row) }
                ?.takeIf { it.row != next.row && it.row !in delivered }
            DeliveryPlan(ordered, delivered, next, after, skipped, learned.isNotEmpty())
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DeliveryPlan())

    private val debouncedQuery = _query.debounce { if (it.isEmpty()) 0L else 160L }

    private data class Filters(val query: String, val field: Set<SearchField>, val tab: DeliveryTab, val notes: DeliveryNotes)

    val ui: StateFlow<DeliveryUi> =
        combine(
            combine(plan, repo.deliveries, repo.session) { p, d, s -> Triple(p, d, s) },
            combine(debouncedQuery, _field, _tab, repo.deliveryNotes) { q, f, t, n -> Filters(q, f, t, n) },
            tracker.fix,
            repo.search
        ) { (p, deliveries, session), filters, fix, engine ->
            val pendingAll = p.plan.filter { it.row !in p.deliveredRows }
            val pending = if (filters.query.isBlank()) pendingAll
            else engine.search(filters.query, filters.field, pendingAll)
            val doneSorted = deliveries.sortedByDescending { it.order }
            val done = if (filters.query.isBlank()) doneSorted else {
                val rows = engine.search(filters.query, filters.field, p.plan.filter { it.row in p.deliveredRows })
                    .map { it.row }.toHashSet()
                doneSorted.filter { it.row in rows }
            }
            val next = p.next
            val dayStart = Dashboard.startOfDay(System.currentTimeMillis())
            val todayTimes = deliveries.filter { it.bulk == 0L && it.deliveredAt >= dayStart }.map { it.deliveredAt }
            val perHour = Pace.perHour(todayTimes)
            val remaining = p.plan.size - p.deliveredRows.size
            DeliveryUi(
                hasRoute = p.plan.isNotEmpty(),
                session = session,
                total = p.plan.size,
                delivered = p.deliveredRows.size,
                deliveredToday = todayTimes.size,
                next = next,
                after = p.after,
                afterDistance = distanceTo(fix, p.after),
                skippedRows = p.skipped.filter { it !in p.deliveredRows }.toSet(),
                perHour = perHour,
                etaMinutes = Pace.etaMinutes(remaining, perHour),
                lastDelivery = deliveries.filter { it.deliveredAt >= dayStart }.maxByOrNull { it.order },
                nextDistance = distanceTo(fix, next),
                nextBearing = bearingTo(fix, next),
                nearby = nearest(fix, pendingAll, next),
                pending = pending,
                done = done,
                searching = filters.query.isNotBlank(),
                learned = p.learned,
                notes = filters.notes,
                noteSuggestions = filters.notes.suggestions(),
                stats = DeliveryDashboard.compute(p.plan, deliveries, p.skipped, System.currentTimeMillis(), dayStart)
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeliveryUi())

    private fun distanceTo(fix: GeoFix?, r: MeterRecord?): Double? {
        if (fix == null || r == null) return null
        val lat = r.lat ?: return null
        val lon = r.lon ?: return null
        return Geo.distanceMeters(fix.lat, fix.lon, lat, lon)
    }

    private fun bearingTo(fix: GeoFix?, r: MeterRecord?): Double? {
        if (fix == null || r == null) return null
        val lat = r.lat ?: return null
        val lon = r.lon ?: return null
        return Geo.bearingDegrees(fix.lat, fix.lon, lat, lon)
    }

    /** "Estás a 12 m del NIR 004": el pendiente más cercano dentro de 25 m. */
    private fun nearest(fix: GeoFix?, pending: List<MeterRecord>, next: MeterRecord?): Nearby? {
        if (fix == null || (fix.accuracy ?: 999f) > 30f) return null
        var best: MeterRecord? = null
        var bestD = NEARBY_METERS
        for (r in pending) {
            val lat = r.lat ?: continue
            val lon = r.lon ?: continue
            val d = Geo.distanceMeters(fix.lat, fix.lon, lat, lon)
            if (d < bestD) { bestD = d; best = r }
        }
        val b = best ?: return null
        if (b.row == next?.row) return null   // ya se muestra como "siguiente"
        return Nearby(b, bestD)
    }

    // ---------------- Acciones ----------------

    fun startDay(onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.startSession()
                DeliveryTrackingService.start(app)
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo iniciar")
            }
        }
    }

    fun endDay() {
        viewModelScope.launch {
            repo.endSession()
            DeliveryTrackingService.stop(app)
        }
    }

    fun deliver(row: Int, note: String = "", onResult: (String) -> Unit) {
        viewModelScope.launch {
            val hadSession = repo.session.value != null
            val atTap = tracker.fix.value
            val ok = repo.deliver(row, atTap, note)
            if (!hadSession && repo.session.value != null) DeliveryTrackingService.start(app)
            val r = repo.recordByRow(row)
            onResult(if (ok) "Entregada: NIR ${r?.nir ?: ""}" else "Esa factura ya estaba entregada")
            if (ok && prefs.settings.value.refineOnDeliver) refineLocationAfterDelivery(row, atTap, onResult)
        }
    }

    /**
     * Al entregar estás parado en la puerta: es una medición buena del medidor. Se promedian
     * unos segundos de GPS y se combina con el punto que ya había (de la lectura o de otra
     * entrega), así la ubicación mejora con cada visita. Va en segundo plano: no te hace esperar.
     */
    private fun refineLocationAfterDelivery(row: Int, atTap: GeoFix?, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val capture = tracker.captureBest(maxWaitMs = 3_000L, goodAccuracy = 12f, minSamples = 3) ?: return@launch
            val captured = capture.fix.accuracy ?: return@launch
            if (captured > REFINE_MAX_ACCURACY) return@launch
            // Si ya te alejaste de la puerta mientras se promediaba, vale la posición del toque.
            val tap = atTap?.accuracy?.let { Spot(atTap.lat, atTap.lon, it) }
            val spot = LocationFusion.steady(tap, Spot(capture.fix.lat, capture.fix.lon, captured)) ?: return@launch
            if (spot.accuracy > REFINE_MAX_ACCURACY) return@launch
            val before = repo.recordByRow(row)
            when (repo.setLocationIfBetter(row, spot.lat, spot.lon, spot.accuracy, "entrega")) {
                LecturasRepository.LocationUpdate.SAVED -> onResult("Ubicación del medidor guardada (±${spot.accuracy.toInt()} m)")
                LecturasRepository.LocationUpdate.IMPROVED ->
                    onResult("Ubicación afinada (±${(before?.locationAccuracy ?: spot.accuracy).toInt()} m)")
                LecturasRepository.LocationUpdate.CORRECTED -> {
                    val lat = before?.lat
                    val lon = before?.lon
                    val far = if (lat != null && lon != null)
                        " (estaba a ${Geo.formatDistance(Geo.distanceMeters(lat, lon, spot.lat, spot.lon))})" else ""
                    onResult("Ubicación corregida$far")
                }
                LecturasRepository.LocationUpdate.CORRECTED_BY_NEIGHBORS -> {
                    val lat = before?.lat
                    val lon = before?.lon
                    val far = if (lat != null && lon != null)
                        " (estaba a ${Geo.formatDistance(Geo.distanceMeters(lat, lon, spot.lat, spot.lon))})" else ""
                    onResult("Ubicación corregida$far · coincide con los vecinos")
                }
                LecturasRepository.LocationUpdate.PROPOSED ->
                    onResult("El punto que pusiste a mano parece estar lejos. Te pregunto qué hacer.")
                LecturasRepository.LocationUpdate.WEAK, LecturasRepository.LocationUpdate.KEPT -> Unit
            }
        }
    }

    /** "La visito después": la deja para el final y muestra la siguiente. */
    fun skipNext(row: Int, onResult: (String) -> Unit) {
        repo.skipForNow(row)
        val r = repo.recordByRow(row)
        onResult("NIR ${r?.nir ?: ""} queda para después")
    }

    fun includeSkipped() = repo.clearSkippedForNow()

    /** Guarda dónde se dejó la factura (texto vacío = borrar la nota). */
    fun setNote(delivery: Delivery, note: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            repo.setDeliveryNote(delivery.id, note)
            onResult(if (note.isBlank()) "Nota borrada" else "Guardado: ${DeliveryNotes.clean(note)}")
        }
    }

    fun deliverBatch(rows: List<Int>, note: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val hadSession = repo.session.value != null
            val n = repo.deliverBatch(rows, note)
            if (!hadSession && repo.session.value != null) DeliveryTrackingService.start(app)
            onResult(if (n > 0) "Paquete entregado: $n facturas registradas" else "No había facturas pendientes para registrar")
        }
    }

    fun undoBatch(stamp: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val n = repo.undoBatch(stamp)
            onResult("Paquete deshecho: $n facturas vuelven a pendientes")
        }
    }

    fun undo(delivery: Delivery, onResult: (String) -> Unit) {
        viewModelScope.launch {
            repo.undoDelivery(delivery.id)
            onResult("Entrega del NIR ${delivery.nir} deshecha")
        }
    }

    companion object {
        const val SCOPE = "delivery"
        private const val NEARBY_METERS = 25.0
        private const val REFINE_MAX_ACCURACY = 40f
    }
}
