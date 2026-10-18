package com.lecturasgas.beta.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lecturasgas.beta.appContainer
import com.lecturasgas.beta.data.model.TrackPoint
import com.lecturasgas.beta.data.repository.RoutePlanner
import com.lecturasgas.beta.readings.ReadOrder
import com.lecturasgas.beta.readings.ReadTrail
import com.lecturasgas.beta.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MapMode(val label: String) { READINGS("Lecturas"), DELIVERY("Entrega") }

/** Un punto listo para dibujar (ya con su color). */
data class MapPoint(
    val row: Int,
    val lat: Double,
    val lon: Double,
    val color: String,
    val radius: Float,
    val nir: String
)

data class MapData(
    val points: List<MapPoint> = emptyList(),
    val withoutGps: Int = 0,
    val total: Int = 0,
    val nextRow: Int? = null,
    /** Ruta recomendada: próximos puntos pendientes en el orden sugerido. */
    val plan: List<Pair<Double, Double>> = emptyList(),
    /** Puntos que parecen mal ubicados (lejos de su barrio o de sus vecinos en la ruta). */
    val doubtful: List<Pair<Double, Double>> = emptyList()
)

class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.appContainer.repository
    private val prefs = app.appContainer.prefs

    private val _mode = MutableStateFlow(if (repo.session.value != null) MapMode.DELIVERY else MapMode.READINGS)
    val mode: StateFlow<MapMode> = _mode.asStateFlow()

    private val _satellite = MutableStateFlow(prefs.satellite)
    val satellite: StateFlow<Boolean> = _satellite.asStateFlow()

    /** Mostrar/ocultar la ruta recomendada (naranja) y tu recorrido (morado). */
    private val _showPlan = MutableStateFlow(prefs.showPlan)
    val showPlan: StateFlow<Boolean> = _showPlan.asStateFlow()

    private val _showTrack = MutableStateFlow(prefs.showTrack)
    val showTrack: StateFlow<Boolean> = _showTrack.asStateFlow()

    /** Mostrar el orden en que registraste las lecturas (hoy), numerado y unido por una línea. */
    private val _showReadOrder = MutableStateFlow(false)
    val showReadOrder: StateFlow<Boolean> = _showReadOrder.asStateFlow()
    fun toggleReadOrder() { _showReadOrder.value = !_showReadOrder.value }

    /** Día que se está viendo en «Rastro» y «Orden lecturas» (inicio del día). null = hoy / el último con datos. */
    private val _day = MutableStateFlow<Long?>(null)
    val day: StateFlow<Long?> = _day.asStateFlow()
    fun setDay(d: Long?) { _day.value = d }

    /** Días con lecturas, entregas o recorrido (el más reciente primero). */
    val days: StateFlow<List<Long>> =
        combine(repo.records, repo.deliveries) { records, deliveries ->
            (ReadOrder.days(records) + deliveries.map { com.lecturasgas.beta.readings.Dashboard.startOfDay(it.deliveredAt) })
                .distinct().sortedDescending()
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val readTrail: StateFlow<ReadTrail?> =
        combine(repo.records, _showReadOrder, _day) { records, show, day ->
            if (!show) null
            else {
                // Los puntos dudosos no se unen a la línea: la enredarían.
                val doubtful = com.lecturasgas.beta.location.LocationOutliers.find(records).map { it.record.row }.toHashSet()
                ReadOrder.trail(records, System.currentTimeMillis(), day = day, exclude = doubtful)
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selected = MutableStateFlow<Int?>(null)
    val selected: StateFlow<Int?> = _selected.asStateFlow()

    /** Fila cuyo punto se está ubicando/ajustando con el pin fijo. */
    private val _adjusting = MutableStateFlow<Int?>(null)
    val adjusting: StateFlow<Int?> = _adjusting.asStateFlow()

    private val _follow = MutableStateFlow(false)
    val follow: StateFlow<Boolean> = _follow.asStateFlow()

    /** Rastro del día elegido: en vivo si es hoy, o el guardado de ese día. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val track: StateFlow<List<TrackPoint>> =
        _day.flatMapLatest { d ->
            if (d == null) repo.track else flow { emit(repo.dayTrack(d)) }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val session = repo.session

    fun setMode(m: MapMode) { _mode.value = m }
    fun toggleSatellite() { _satellite.value = !_satellite.value; prefs.satellite = _satellite.value }
    fun togglePlan() { _showPlan.value = !_showPlan.value; prefs.showPlan = _showPlan.value }
    fun toggleTrack() { _showTrack.value = !_showTrack.value; prefs.showTrack = _showTrack.value }
    fun select(row: Int?) { _selected.value = row }
    fun setFollow(v: Boolean) { _follow.value = v }

    private val _headingUp = MutableStateFlow(prefs.mapHeadingUp)
    /** true: el mapa gira hacia donde avanzas. false: el norte siempre arriba. */
    val headingUp: StateFlow<Boolean> = _headingUp.asStateFlow()
    fun toggleHeadingUp() { _headingUp.value = !_headingUp.value; prefs.mapHeadingUp = _headingUp.value }
    fun startAdjust(row: Int) { _selected.value = row; _adjusting.value = row; _follow.value = false }
    fun cancelAdjust() { _adjusting.value = null }

    fun saveAdjust(lat: Double, lon: Double, onDone: (String) -> Unit) {
        val row = _adjusting.value ?: return
        viewModelScope.launch {
            repo.setLocation(row, lat, lon, null, "manual")
            _adjusting.value = null
            onDone("Ubicación guardada")
        }
    }

    fun deleteLocation(row: Int, onDone: (String) -> Unit) {
        viewModelScope.launch {
            repo.deleteLocation(row)
            _selected.value = null
            onDone("Ubicación borrada")
        }
    }

    init {
        // Al iniciar una jornada, el mapa pasa solo al modo Entrega.
        viewModelScope.launch {
            var hadSession = repo.session.value != null
            repo.session.collect { s ->
                val has = s != null
                if (has && !hadSession && !repo.startedFromReadings) _mode.value = MapMode.DELIVERY
                hadSession = has
            }
        }
    }

    val data: StateFlow<MapData> =
        combine(repo.records, repo.deliveries, repo.learned, _mode, repo.skippedForNow) { records, deliveries, learned, mode, skipped ->
            val delivered = deliveries.map { it.row }.toHashSet()
            val done = delivered + skipped          // las "saltadas" se dejan para después
            var nextRow: Int? = null
            var plan: List<Pair<Double, Double>> = emptyList()
            if (mode == MapMode.DELIVERY) {
                val ordered = RoutePlanner.plan(records, learned)
                val last = deliveries.maxByOrNull { it.order }?.row
                val next = RoutePlanner.next(ordered, done, last) ?: RoutePlanner.next(ordered, delivered, last)
                nextRow = next?.row
                if (next != null) {
                    plan = RoutePlanner.shortPath(
                        plan = ordered,
                        delivered = done,
                        start = next,
                        useLearned = learned.isNotEmpty(),
                        count = PLAN_POINTS
                    ).map { (it.lat ?: 0.0) to (it.lon ?: 0.0) }
                }
            }
            val points = ArrayList<MapPoint>(records.size)
            for (r in records) {
                val lat = r.lat ?: continue
                val lon = r.lon ?: continue
                val (color, radius) = when (mode) {
                    MapMode.READINGS -> when {
                        r.isRead -> AppColors.DONE_HEX to 6.5f
                        r.skipReason != null -> AppColors.SKIPPED_HEX to 7.5f
                        else -> AppColors.PENDING_HEX to 7.5f
                    }
                    MapMode.DELIVERY -> when {
                        r.row == nextRow -> AppColors.NEXT_HEX to 11f
                        r.row in delivered -> AppColors.DELIVERED_HEX to 6.5f
                        else -> AppColors.PENDING_HEX to 7.5f
                    }
                }
                points.add(MapPoint(r.row, lat, lon, color, radius, r.nir))
            }
            val doubtful = com.lecturasgas.beta.location.LocationOutliers.find(records)
                .mapNotNull { sp -> val la = sp.record.lat; val lo = sp.record.lon; if (la != null && lo != null) la to lo else null }
            MapData(points, records.count { !it.hasLocation }, records.size, nextRow, plan, doubtful)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapData())

    companion object {
        /** Cuántos puntos se dibujan en la línea naranja (la siguiente + las más cercanas). */
        private const val PLAN_POINTS = 5
    }
}
