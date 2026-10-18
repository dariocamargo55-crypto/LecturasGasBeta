package com.lecturasgas.beta.map

import com.lecturasgas.beta.readings.ReadTrail
import com.lecturasgas.beta.readings.ReadOrder
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.RectF
import android.location.Location
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import com.lecturasgas.beta.ui.components.ProximityBanner
import com.lecturasgas.beta.location.CompassSensor
import com.lecturasgas.beta.location.Heading
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import com.lecturasgas.beta.ui.icons.AppIcons
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.delivery.DeliveryViewModel
import com.lecturasgas.beta.ui.MainViewModel
import com.lecturasgas.beta.ui.components.Dot
import com.lecturasgas.beta.ui.components.Segmented
import com.lecturasgas.beta.ui.components.StatusPill
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.ui.theme.MeterCodeStyle
import com.lecturasgas.beta.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point

/** Centro por defecto: Moniquirá (Boyacá). */
private const val COMPASS_MIN_TURN = 8.0
private val DEFAULT_CENTER = LatLng(5.8764, -73.5730)

/** false = el mapa NO se oscurece con el modo oscuro de la app. Cámbialo a true para volver al mapa oscuro. */
private const val MAP_FOLLOWS_DARK_THEME = false

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    main: MainViewModel,
    vm: MapViewModel = viewModel(),
    deliveryVm: DeliveryViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val data by vm.data.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val satellite by vm.satellite.collectAsStateWithLifecycle()
    val selectedRow by vm.selected.collectAsStateWithLifecycle()
    val adjustingRow by vm.adjusting.collectAsStateWithLifecycle()
    val follow by vm.follow.collectAsStateWithLifecycle()
    val headingUp by vm.headingUp.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val showPlan by vm.showPlan.collectAsStateWithLifecycle()
    val showTrack by vm.showTrack.collectAsStateWithLifecycle()
    val showReadOrder by vm.showReadOrder.collectAsStateWithLifecycle()
    val gpsEnabled by main.gpsEnabled.collectAsStateWithLifecycle()
    val near by main.near.collectAsStateWithLifecycle()
    val settings by main.settings.collectAsStateWithLifecycle()
    val improving by main.improving.collectAsStateWithLifecycle()
    val readTrail by vm.readTrail.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val fix by main.fix.collectAsStateWithLifecycle()
    val records by main.repo.records.collectAsStateWithLifecycle()
    val deliveries by main.repo.deliveries.collectAsStateWithLifecycle()
    val mapRequest by main.mapRequest.collectAsStateWithLifecycle()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var toolsOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val mapScope = androidx.compose.runtime.rememberCoroutineScope()
    var style by remember { mutableStateOf<Style?>(null) }
    var styleIsDark by remember { mutableStateOf(false) }
    var triedLightFallback by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var initialCameraDone by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<LatLng?>(null) }
    var confirmDeleteGps by remember { mutableStateOf(false) }
    val offlineMaps = remember { OfflineMapController(context).also { it.enlargeCache() } }
    var offlineZone by remember { mutableStateOf<LatLngBounds?>(null) }
    var showOfflineDialog by remember { mutableStateOf(false) }

    val selectedRecord: MeterRecord? = remember(selectedRow, records) {
        selectedRow?.let { row -> records.firstOrNull { it.row == row } }
    }
    val deliveredRows = remember(deliveries) { deliveries.map { it.row }.toHashSet() }

    // Valores "vivos" para usar dentro de los listeners del mapa (que se crean una sola vez).
    val adjustingNow by rememberUpdatedState(adjustingRow)
    val satelliteNow by rememberUpdatedState(satellite)

    // ---------------- MapView y su ciclo de vida ----------------
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        // textureMode: se comporta mejor dentro del deslizador entre módulos.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { mapView.onPause() }
            runCatching { mapView.onStop() }
            runCatching { mapView.onDestroy() }
        }
    }
    DisposableEffect(mapView) {
        val listener = MapView.OnDidFailLoadingMapListener {
            // Sin internet: se carga un estilo local para que los puntos se sigan viendo.
            val m = map
            // Modo oscuro sin internet: el mapa oscuro no se descarga; se prueba primero el claro guardado.
            if (!offline && m != null && styleIsDark && !triedLightFallback) {
                triedLightFallback = true
                styleIsDark = false
                m.setStyle(Style.Builder().fromUri(MapLayers.STREETS_STYLE)) { s ->
                    MapLayers.install(s, satelliteNow)
                    style = s
                }
                return@OnDidFailLoadingMapListener
            }
            if (!offline && m != null) {
                offline = true
                m.setStyle(Style.Builder().fromJson(MapLayers.OFFLINE_STYLE)) { s ->
                    MapLayers.install(s, false)
                    style = s
                }
            }
        }
        mapView.addOnDidFailLoadingMapListener(listener)
        onDispose { mapView.removeOnDidFailLoadingMapListener(listener) }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.isAttributionEnabled = true
            m.uiSettings.isLogoEnabled = false
            m.setMaxZoomPreference(20.0)
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 14.0))

            m.addOnMapClickListener { latLng ->
                if (adjustingNow != null) return@addOnMapClickListener false
                val pt = m.projection.toScreenLocation(latLng)
                val r = with(density) { 22.dp.toPx() }
                val box = RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r)
                val clusters = m.queryRenderedFeatures(box, MapLayers.LAYER_CLUSTERS)
                if (clusters.isNotEmpty()) {
                    val g = clusters.first().geometry() as? Point
                    val target = if (g != null) LatLng(g.latitude(), g.longitude()) else latLng
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(target, m.cameraPosition.zoom + 2.0), 350)
                    return@addOnMapClickListener true
                }
                val hit = m.queryRenderedFeatures(box, MapLayers.LAYER_POINTS).firstOrNull()
                val row = hit?.getNumberProperty("row")?.toInt()
                vm.select(row)
                true
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) vm.setFollow(false)
            }
            m.addOnCameraMoveListener { pinTarget = m.cameraPosition.target }
            m.addOnCameraIdleListener { pinTarget = m.cameraPosition.target }

            map = m
            styleIsDark = MAP_FOLLOWS_DARK_THEME && AppColors.dark   // el mapa siempre es claro
            m.setStyle(Style.Builder().fromUri(if (styleIsDark) MapLayers.DARK_STYLE else MapLayers.STREETS_STYLE)) { s ->
                MapLayers.install(s, satelliteNow)
                style = s
            }
        }
    }

    // El mapa se queda en su estilo claro aunque la app esté en modo oscuro (así se ven bien los detalles).
    val darkTheme = MAP_FOLLOWS_DARK_THEME && AppColors.dark
    LaunchedEffect(darkTheme, map) {
        val m = map ?: return@LaunchedEffect
        if (styleIsDark == darkTheme) return@LaunchedEffect
        styleIsDark = darkTheme
        m.setStyle(Style.Builder().fromUri(if (darkTheme) MapLayers.DARK_STYLE else MapLayers.STREETS_STYLE)) { s ->
            MapLayers.install(s, satelliteNow)
            style = s
        }
    }

    // ---------------- Punto azul "mi ubicación" ----------------
    var locationActivated by remember { mutableStateOf(false) }
    // Rumbo suavizado (hacia dónde avanzas) y el último fix, para calcularlo.
    var heading by remember { mutableStateOf<Double?>(null) }
    var prevFix by remember { mutableStateOf<GeoFix?>(null) }
    var shownBearing by remember { mutableStateOf<Double?>(null) }
    val rotating = follow && headingUp && adjustingRow == null

    // ---------------- Brújula: gira el mapa también cuando estás parado ----------------
    val compass = remember { CompassSensor(context) }
    val compassDeg by compass.heading.collectAsStateWithLifecycle()
    val useCompass = settings.useCompass && rotating && compass.available
    DisposableEffect(useCompass) {
        if (useCompass) compass.start()
        onDispose { compass.stop() }
    }
    LaunchedEffect(fix != null) { fix?.let { compass.setLocation(it.lat, it.lon) } }
    LaunchedEffect(compassDeg, useCompass) {
        val c = compassDeg ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        if (!useCompass) return@LaunchedEffect
        // Si caminas, manda el rumbo del GPS (más estable); parado, manda la brújula.
        if ((fix?.speed ?: 0f) >= Heading.MIN_SPEED) return@LaunchedEffect
        val shown = shownBearing
        if (shown != null && abs(Heading.delta(shown, c)) < COMPASS_MIN_TURN) return@LaunchedEffect
        heading = c
        shownBearing = c
        runCatching {
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(c).tilt(0.0).build()), 250
            )
        }
    }
    LaunchedEffect(style, fix) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        heading = Heading.next(heading, prevFix, f)
        prevFix = f
        runCatching {
            val lc = m.locationComponent
            if (!locationActivated || !lc.isLocationComponentActivated) {
                lc.activateLocationComponent(
                    LocationComponentActivationOptions.builder(context, s)
                        .useDefaultLocationEngine(false)
                        .build()
                )
                lc.isLocationComponentEnabled = true
                lc.cameraMode = CameraMode.NONE
                locationActivated = true
            }
            // Girando, el punto se vuelve una flecha que apunta hacia donde avanzas.
            lc.renderMode = if (rotating) RenderMode.GPS else RenderMode.NORMAL
            lc.forceLocationUpdate(f.toAndroidLocation())
        }
        if (follow && adjustingRow == null) {
            val here = LatLng(f.lat, f.lon)
            val h = heading
            if (headingUp && h != null) {
                // Solo gira cuando el cambio es notorio, para que el mapa no tiemble.
                val bearing = if (Heading.shouldTurn(shownBearing, h)) h else (shownBearing ?: h)
                shownBearing = bearing
                m.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder(m.cameraPosition).target(here).bearing(bearing).tilt(0.0).build()
                    ), 700
                )
            } else {
                m.animateCamera(CameraUpdateFactory.newLatLng(here), 600)
            }
        }
    }

    // Al girar, tú quedas en el tercio de abajo de la pantalla (se ve más camino por delante).
    LaunchedEffect(map, rotating) {
        val m = map ?: return@LaunchedEffect
        val top = if (rotating) (mapView.height * 0.30f).toInt() else 0
        runCatching { m.setPadding(0, top, 0, 0) }
    }

    // ---------------- Datos -> capas ----------------
    LaunchedEffect(style, data) {
        val s = style ?: return@LaunchedEffect
        val points = withContext(Dispatchers.Default) { MapLayers.pointsCollection(data.points) }
        MapLayers.setData(s, MapLayers.SRC_METERS, points)
        MapLayers.setData(s, MapLayers.SRC_DOUBTFUL, MapLayers.plainPoints(data.doubtful))
        if (!initialCameraDone && data.points.isNotEmpty()) {
            initialCameraDone = true
            fitAll(map, data.points.map { LatLng(it.lat, it.lon) }, density.run { 48.dp.roundToPx() })
        }
    }
    // Ruta recomendada: tu posición -> siguiente -> los más cercanos (máx. 5 puntos).
    LaunchedEffect(style, data.plan, fix, showPlan, mode) {
        val s = style ?: return@LaunchedEffect
        val here = fix
        val line =
            if (!showPlan || mode != MapMode.DELIVERY || data.plan.isEmpty()) emptyList()
            else if (here != null) listOf(here.lat to here.lon) + data.plan
            else data.plan
        MapLayers.setData(s, MapLayers.SRC_PLAN, MapLayers.line(line))
    }
    // Orden en que registraste las lecturas (hoy): línea + números.
    LaunchedEffect(style, readTrail) {
        val s = style ?: return@LaunchedEffect
        val pts = readTrail?.points.orEmpty()
        MapLayers.setData(s, MapLayers.SRC_ORDER_LINE, MapLayers.line(pts.map { it.lat to it.lon }))
        MapLayers.setData(s, MapLayers.SRC_ORDER_POINTS, MapLayers.orderPoints(pts))
    }
    LaunchedEffect(style, track, showTrack) {
        val s = style ?: return@LaunchedEffect
        val show = showTrack
        MapLayers.setData(s, MapLayers.SRC_TRACK, if (show) MapLayers.trackLine(track) else MapLayers.line(emptyList()))
    }
    LaunchedEffect(style, selectedRecord, adjustingRow) {
        val s = style ?: return@LaunchedEffect
        val r = selectedRecord
        MapLayers.setData(
            s, MapLayers.SRC_SELECTED,
            if (adjustingRow != null) MapLayers.singlePoint(null, null) else MapLayers.singlePoint(r?.lat, r?.lon)
        )
    }
    LaunchedEffect(style, satellite) {
        style?.let { MapLayers.setSatellite(it, satellite) }
    }

    // ---------------- Pedidos desde Lecturas / Entrega ----------------
    LaunchedEffect(mapRequest, map) {
        val req = mapRequest ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val record = main.repo.recordByRow(req.row)
        main.consumeMapRequest()
        if (record == null) return@LaunchedEffect
        val lat = record.lat
        val lon = record.lon
        val here = fix
        val target = when {
            lat != null && lon != null -> LatLng(lat, lon)
            here != null -> LatLng(here.lat, here.lon)
            else -> null
        }
        // Sin esto, si estabas en «Siguiendo», el mapa volvía a tu posición y tapaba el medidor.
        vm.setFollow(false)
        shownBearing = null
        if (req.adjust) vm.startAdjust(record.row) else vm.select(record.row)
        if (target != null) {
            val zoom = if (req.adjust) 19.0 else 18.0
            // Primero un salto directo (funciona aunque la pantalla del mapa aún no se vea)...
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(target, zoom))
            // ...y cuando el mapa ya está en pantalla, se vuelve a centrar con animación suave.
            // (en otra corrutina: este efecto se reinicia al consumir el pedido)
            mapScope.launch {
                delay(450)
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom), 400)
            }
        }
    }

    BackHandler(enabled = adjustingRow != null || selectedRow != null) {
        if (adjustingRow != null) vm.cancelAdjust() else vm.select(null)
    }

    // ================= INTERFAZ =================
    var panelOpen by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ----- Arriba: modo + leyenda -----
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (adjustingRow == null) {
                Surface(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = AppColors.Card.copy(alpha = 0.97f),
                    shadowElevation = 3.dp
                ) {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        // Fila compacta: modo + estado del GPS + plegar el panel (así el mapa gana espacio).
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MapModeToggle(mode, vm::setMode, Modifier.weight(1f))
                            GpsPill(fix, gpsEnabled) {
                                if (!gpsEnabled) runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                                else main.refreshLocation()
                            }
                            Box(
                                Modifier.size(34.dp).clickable { panelOpen = !panelOpen },
                                contentAlignment = Alignment.Center
                            ) { Text(if (panelOpen) "▲" else "▼", fontSize = 14.sp, color = AppColors.Muted) }
                        }
                        if (panelOpen) {
                        Legend(mode, data.withoutGps, offline, data.doubtful.size)
                        LayerChips(
                            plan = showPlan && mode == MapMode.DELIVERY,
                            onPlan = {
                                if (mode == MapMode.DELIVERY) vm.togglePlan()
                                else main.showMessage("La ruta recomendada se ve en el modo Entrega")
                            },
                            track = showTrack,
                            onTrack = { vm.toggleTrack() },
                            order = showReadOrder,
                            onOrder = {
                                vm.toggleReadOrder()
                                if (!showReadOrder) {
                                    // Al encenderlo, se acerca el mapa al recorrido (si ya hay lecturas con GPS).
                                    val trail = ReadOrder.trail(records, System.currentTimeMillis(), day = day)
                                    val pts = trail?.points.orEmpty()
                                    if (pts.isEmpty()) {
                                        main.showMessage(
                                            if (trail == null) "Aún no has registrado lecturas"
                                            else "Esas lecturas no tienen ubicación GPS guardada"
                                        )
                                    } else {
                                        vm.setFollow(false)
                                        fitAll(map, pts.map { LatLng(it.lat, it.lon) }, density.run { 64.dp.roundToPx() })
                                    }
                                }
                            },
                            dayVisible = showTrack || showReadOrder,
                            dayText = dayLabel(day),
                            days = days,
                            onDay = { vm.setDay(it) },
                            offline = offline,
                            onOffline = {
                                // Zona = todos los medidores con GPS; si no hay, lo que se ve en pantalla.
                                offlineZone = workZoneBounds(
                                    records,
                                    runCatching { map?.projection?.visibleRegion?.latLngBounds }.getOrNull()
                                )
                                showOfflineDialog = true
                            }
                        )
                        if (showTrack) TrackInfo(track, day, session != null)
                        if (showReadOrder) ReadOrderInfo(readTrail)
                        }
                    }
                }
                if (mode == MapMode.DELIVERY) {
                    val next = data.nextRow?.let { row -> records.firstOrNull { it.row == row } }
                    if (next != null && selectedRow == null) {
                        NextBanner(
                            record = next,
                            fix = fix,
                            onTap = {
                                vm.select(next.row)
                                val lat = next.lat
                                val lon = next.lon
                                if (lat != null && lon != null) {
                                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 18.0), 500)
                                }
                            },
                            onDeliver = { deliveryVm.deliver(next.row) { main.showMessage(it) } }
                        )
                    }
                }
            } else {
                AdjustBanner(records.firstOrNull { it.row == adjustingRow }, pinTarget)
            }
        }

        // ----- Derecha: un solo botón que despliega las herramientas -----
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp, top = 150.dp)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapButton(
                if (toolsOpen) AppIcons.Close else AppIcons.Menu,
                if (toolsOpen) "Cerrar" else "Más",
                active = toolsOpen || follow || satellite
            ) { toolsOpen = !toolsOpen }
            if (toolsOpen) {
                MapButton(AppIcons.Search, "Buscar", active = false) { toolsOpen = false; showSearch = true }
                MapButton(AppIcons.Locate, if (follow) "Sigue" else "Yo", active = follow) {
                    val f = fix
                    if (f == null) {
                        main.showMessage("Esperando señal GPS…")
                        main.refreshLocation()
                    } else {
                        toolsOpen = false
                        vm.setFollow(!follow)
                        val m = map
                        if (m != null) {
                            val b = if (headingUp) (heading ?: shownBearing ?: m.cameraPosition.bearing) else m.cameraPosition.bearing
                            shownBearing = b
                            m.animateCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder().target(LatLng(f.lat, f.lon)).zoom(18.0).bearing(b).tilt(0.0).build()
                                ), 500
                            )
                        }
                    }
                }
                MapButton(AppIcons.North, if (headingUp) "Gira" else "Norte", active = headingUp) {
                    toolsOpen = false
                    vm.toggleHeadingUp()
                    if (headingUp) {
                        // Se apagó el giro: el norte vuelve arriba.
                        shownBearing = null
                        map?.let { m ->
                            m.animateCamera(
                                CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).build()), 500
                            )
                        }
                        main.showMessage("Norte siempre arriba")
                    } else {
                        main.showMessage("El mapa gira hacia donde avanzas (toca «Yo» para seguirte)")
                    }
                }
                MapButton(AppIcons.Layers, if (satellite) "Calles" else "Satélite", active = satellite) { toolsOpen = false; vm.toggleSatellite() }
                MapButton(AppIcons.FitAll, "Todo", active = false) {
                    toolsOpen = false
                    vm.setFollow(false)
                    shownBearing = null
                    map?.let { m -> m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).build()), 300) }
                    fitAll(map, data.points.map { LatLng(it.lat, it.lon) }, density.run { 48.dp.roundToPx() })
                }
            }
        }

        // ----- Izquierda: zoom + / − -----
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 10.dp, top = 150.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppColors.Card, shadowElevation = 4.dp) {
                Column {
                    Box(
                        Modifier.size(48.dp).clickable { map?.animateCamera(CameraUpdateFactory.zoomIn(), 250) },
                        contentAlignment = Alignment.Center
                    ) { Icon(AppIcons.Plus, contentDescription = "Acercar", tint = MaterialTheme.colorScheme.primary) }
                    Box(Modifier.width(48.dp).height(1.dp).background(AppColors.RingTrack))
                    Box(
                        Modifier.size(48.dp).clickable { map?.animateCamera(CameraUpdateFactory.zoomOut(), 250) },
                        contentAlignment = Alignment.Center
                    ) { Icon(AppIcons.Minus, contentDescription = "Alejar", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        if (showSearch) {
            MapSearchDialog(
                records = records,
                onPick = { r ->
                    showSearch = false
                    vm.setFollow(false)
                    vm.select(r.row)
                    val lat = r.lat
                    val lon = r.lon
                    if (lat != null && lon != null) {
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 18.5), 500)
                    }
                },
                onDismiss = { showSearch = false }
            )
        }

        if (showOfflineDialog) {
            OfflineMapDialog(
                controller = offlineMaps,
                zone = offlineZone,
                onDismiss = { showOfflineDialog = false }
            )
        }

        // ----- Pin fijo para ajustar ubicación -----
        if (adjustingRow != null) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = AppColors.Delivered,
                modifier = Modifier.align(Alignment.Center).size(52.dp).offset(y = (-24).dp)
            )
            Box(
                Modifier.align(Alignment.Center).size(8.dp).background(Color.Black, CircleShape)
            )
            AdjustActions(
                modifier = Modifier.align(Alignment.BottomCenter),
                onCancel = { vm.cancelAdjust() },
                onUseGps = {
                    val f = fix
                    if (f == null) main.showMessage("Aún no hay señal GPS")
                    else map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.lat, f.lon), 19.0), 400)
                },
                onSave = {
                    val t = map?.cameraPosition?.target
                    if (t != null) vm.saveAdjust(t.latitude, t.longitude) { main.showMessage(it) }
                }
            )
        } else if (selectedRecord != null) {
            val r = selectedRecord
            SelectedCard(
                record = r,
                mode = mode,
                delivered = r.row in deliveredRows,
                fix = fix,
                modifier = Modifier.align(Alignment.BottomCenter),
                onClose = { vm.select(null) },
                onReading = { main.openReading(r.row) },
                onDeliver = { deliveryVm.deliver(r.row) { main.showMessage(it) } },
                onAdjust = { vm.startAdjust(r.row) },
                onDeleteGps = { confirmDeleteGps = true },
                onNavigate = {
                    val lat = r.lat
                    val lon = r.lon
                    if (lat != null && lon != null) {
                        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(NIR ${Uri.encode(r.nir)})")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { main.showMessage("No hay una app de mapas instalada") }
                    }
                }
            )
        } else {
            near?.let { n ->
                ProximityBanner(
                    near = n,
                    improving = improving == n.record.row,
                    onRead = { main.openReading(n.record.row) },
                    onImprove = { main.improveLocation(n.record.row) },
                    onDismiss = { main.dismissNear(n.record.row) },
                    vibrate = settings.nearVibrate,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (confirmDeleteGps && selectedRecord != null) {
        val r = selectedRecord
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteGps = false },
            title = { Text("¿Borrar la ubicación?") },
            text = { Text("El medidor ${r.displayMeter} quedará sin GPS hasta que lo vuelvas a ubicar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteGps = false
                    vm.deleteLocation(r.row) { main.showMessage(it) }
                }) { Text("Borrar", color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteGps = false }) { Text("Cancelar") } }
        )
    }
}

private fun GeoFix.toAndroidLocation(): Location = Location(provider).also {
    it.latitude = lat
    it.longitude = lon
    it.time = time
    accuracy?.let { a -> it.accuracy = a }
    bearing?.let { b -> it.bearing = b }
    speed?.let { s -> it.speed = s }
}

private fun fitAll(map: MapLibreMap?, points: List<LatLng>, padding: Int) {
    val m = map ?: return
    when {
        points.isEmpty() -> Unit
        points.size == 1 -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(points[0], 17.0), 500)
        else -> runCatching {
            val bounds = LatLngBounds.Builder().includes(points).build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 600)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(mode: MapMode, withoutGps: Int, offline: Boolean, doubtful: Int = 0) {
    // FlowRow: si no cabe en una línea, baja a la siguiente en vez de encimarse.
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        val items = if (mode == MapMode.READINGS) listOf(
            AppColors.Pending to "Pendiente", AppColors.Done to "Leído", AppColors.Skipped to "Sin leer"
        ) else listOf(
            AppColors.Pending to "Por entregar", AppColors.Next to "Siguiente", AppColors.Delivered to "Entregada"
        )
        items.forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Dot(c); Text(label, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (doubtful > 0) Text("◯ $doubtful dudosos", fontSize = 12.sp, color = AppColors.Error, fontWeight = FontWeight.Bold, maxLines = 1)
        if (offline) Text("Sin internet", fontSize = 12.sp, color = AppColors.Warning, fontWeight = FontWeight.Bold, maxLines = 1)
        else if (withoutGps > 0) Text("$withoutGps sin GPS", fontSize = 12.sp, color = AppColors.Muted, maxLines = 1)
    }
}

/** Interruptor Lecturas / Entrega, bajito (34 dp) para no gastar espacio del mapa. */
@Composable
private fun MapModeToggle(mode: MapMode, onSelect: (MapMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(AppColors.CardAlt, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MapMode.entries.forEach { m ->
            val sel = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(m) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    m.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Fila de interruptores del mapa, fácil de entender: el estado del GPS primero y luego las
 * capas que se pueden prender o apagar (se desliza hacia los lados).
 */
@Composable
private fun LayerChips(
    plan: Boolean, onPlan: () -> Unit,
    track: Boolean, onTrack: () -> Unit,
    order: Boolean, onOrder: () -> Unit,
    dayVisible: Boolean, dayText: String, days: List<Long>, onDay: (Long?) -> Unit,
    offline: Boolean, onOffline: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item { FilterChip(selected = plan, onClick = onPlan, label = { Text("Ruta") }) }
        item { FilterChip(selected = track, onClick = onTrack, label = { Text("Rastro") }) }
        item { FilterChip(selected = order, onClick = onOrder, label = { Text("Orden lecturas") }) }
        if (dayVisible) item {
            var open by remember { mutableStateOf(false) }
            Column {
                FilterChip(selected = true, onClick = { open = true }, label = { Text("Día: $dayText ▾") })
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("Hoy") }, onClick = { onDay(null); open = false })
                    days.take(30).forEach { d ->
                        DropdownMenuItem(text = { Text(dayLabel(d)) }, onClick = { onDay(d); open = false })
                    }
                }
            }
        }
        item { FilterChip(selected = offline, onClick = onOffline, label = { Text(if (offline) "Sin internet" else "Mapa offline") }) }
    }
}

/** Estado del GPS en una frase: qué tan bueno es y qué hacer. Tócalo para refrescar. */
@Composable
private fun GpsPill(fix: GeoFix?, gpsEnabled: Boolean, onClick: () -> Unit) {
    val fresh = fix != null && System.currentTimeMillis() - fix.time < 60_000L
    val acc = fix?.accuracy
    val (color, text) = when {
        !gpsEnabled -> AppColors.Error to "GPS apagado · toca"
        !fresh -> Color(0xFFFFA000) to "Buscando GPS…"
        acc == null -> Color(0xFF2E7D32) to "GPS activo"
        acc <= 10f -> Color(0xFF2E7D32) to "GPS ±${acc.toInt()} m · Buena"
        acc <= 25f -> Color(0xFFFFA000) to "GPS ±${acc.toInt()} m · Regular"
        else -> AppColors.Error to "GPS ±${acc.toInt()} m · Débil"
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(color)
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

private fun dayLabel(d: Long?): String {
    if (d == null) return "Hoy"
    val today = com.lecturasgas.beta.readings.Dashboard.startOfDay(System.currentTimeMillis())
    return when {
        d == today -> "Hoy"
        d == today - 24L * 3600_000L -> "Ayer"
        else -> java.text.SimpleDateFormat("EEE d MMM", java.util.Locale("es", "CO")).format(java.util.Date(d))
    }
}

/** Resumen del rastro (por dónde caminaste) o por qué no hay. */
@Composable
private fun TrackInfo(track: List<com.lecturasgas.beta.data.model.TrackPoint>, day: Long?, recording: Boolean) {
    val text = if (track.size < 2) {
        if (recording) "Rastro: grabando, camina un poco para verlo"
        else "Rastro: no hay recorrido ${if (day == null) "reciente" else "de ese día"}. Se graba con ▶ o al leer (Ajustes ▸ Grabar mi recorrido al leer)"
    } else {
        var m = 0.0
        for (i in 1 until track.size) m += Geo.distanceMeters(track[i - 1].lat, track[i - 1].lon, track[i].lat, track[i].lon)
        "Rastro · ${dayLabel(day)}: ${Geo.formatDistance(m)}" + if (recording && day == null) " · grabando" else ""
    }
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF546E7A),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
    )
}

/** Resumen del recorrido de lecturas que se está dibujando. */
@Composable
private fun ReadOrderInfo(trail: ReadTrail?) {
    val text = when {
        trail == null -> "Orden de lecturas: no hay lecturas registradas en ese día"
        else -> buildString {
            append("Orden de lecturas · ")
            append(
                if (trail.isToday) "hoy"
                else java.text.SimpleDateFormat("d MMM", java.util.Locale("es", "CO")).format(java.util.Date(trail.dayStart))
            )
            append(": ").append(trail.visited).append(" medidores")
            if (trail.points.size >= 2) append(" · ").append(Geo.formatDistance(trail.meters))
            if (trail.withoutGps > 0) append(" · ").append(trail.withoutGps).append(" sin GPS")
            if (trail.doubtful > 0) append(" · ").append(trail.doubtful).append(" dudosos sin dibujar")
        }
    }
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF00695C),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
    )
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.primary else AppColors.Card,
        contentColor = if (active) Color.White else MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = Modifier.width(62.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun NextBanner(record: MeterRecord, fix: GeoFix?, onTap: () -> Unit, onDeliver: () -> Unit) {
    val lat = record.lat
    val lon = record.lon
    val distance = if (fix != null && lat != null && lon != null) Geo.distanceMeters(fix.lat, fix.lon, lat, lon) else null
    val bearing = if (fix != null && lat != null && lon != null) Geo.bearingDegrees(fix.lat, fix.lon, lat, lon) else null
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onTap),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.TintOrange)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SIGUIENTE", color = AppColors.Next, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1)
                    Text("NIR ${record.nir}", fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
                }
                Text(record.user, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                Text(
                    if (distance != null && bearing != null) "${Geo.arrowFor(bearing)} ${Geo.formatDistance(distance)} · ${record.address}"
                    else record.address,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = AppColors.Muted
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDeliver,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Delivered),
                modifier = Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("ENTREGAR", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun AdjustBanner(record: MeterRecord?, target: LatLng?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (record?.hasLocation == true) "Ajustar ubicación" else "Ubicar medidor",
                fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
            if (record != null) Text("NIR ${record.nir} · ${record.displayMeter} · ${record.user}", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Mueve el mapa con el dedo hasta que el pin quede sobre el medidor.", fontSize = 13.sp)
            if (target != null) {
                Text(
                    String.format(java.util.Locale.US, "%.6f, %.6f", target.latitude, target.longitude),
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun AdjustActions(modifier: Modifier, onCancel: () -> Unit, onUseGps: () -> Unit, onSave: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(22.dp),
        color = AppColors.Card,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                OutlinedButton(onClick = onUseGps, modifier = Modifier.weight(1f)) { Text("Ir a mi GPS") }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Done)
            ) { Text("GUARDAR UBICACIÓN AQUÍ", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun SelectedCard(
    record: MeterRecord,
    mode: MapMode,
    delivered: Boolean,
    fix: GeoFix?,
    modifier: Modifier,
    onClose: () -> Unit,
    onReading: () -> Unit,
    onDeliver: () -> Unit,
    onAdjust: () -> Unit,
    onDeleteGps: () -> Unit,
    onNavigate: () -> Unit
) {
    val lat = record.lat
    val lon = record.lon
    val distance = if (fix != null && lat != null && lon != null) Geo.distanceMeters(fix.lat, fix.lon, lat, lon) else null
    Surface(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        shape = RoundedCornerShape(22.dp),
        color = AppColors.Card,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NIR ${record.nir}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                if (mode == MapMode.DELIVERY) {
                    StatusPill(if (delivered) "Entregada" else "Por entregar", if (delivered) AppColors.Delivered else AppColors.Pending)
                } else {
                    StatusPill(if (record.isRead) "Leído ${record.reading}" else "Pendiente", if (record.isRead) AppColors.Done else AppColors.Pending)
                }
                Spacer(Modifier.weight(1f))
                if (distance != null) Text(Geo.formatDistance(distance), fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Cerrar", modifier = Modifier.size(22.dp)) }
            }
            Text(record.displayMeter, style = MeterCodeStyle)
            Text(record.user, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(record.address, record.barrio).filter { it.isNotBlank() }.joinToString(" · "),
                color = AppColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (record.note.isNotBlank()) Text("Nota: ${record.note}", color = AppColors.Warning, fontSize = 13.sp)
            Text(
                "Ubicación: " + when (record.locationSource) {
                    "manual" -> "ajustada a mano"
                    "lectura" -> "tomada al leer"
                    "entrega" -> "tomada al entregar"
                    "remedido" -> "re-medida en campo"
                    "combinada" -> "afinada con varias visitas"
                    "corregida" -> "corregida al leer/entregar"
                    else -> "guardada"
                } + (record.locationAccuracy?.let { " · ±${it.toInt()} m" } ?: "") +
                        (if (record.hasWeakLocation) " · dudosa" else ""),
                fontSize = 13.sp, color = if (record.hasWeakLocation) AppColors.Warning else AppColors.Muted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == MapMode.DELIVERY && !delivered) {
                    Button(
                        onClick = onDeliver,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Delivered)
                    ) { Text("ENTREGAR", fontWeight = FontWeight.Bold) }
                } else {
                    Button(
                        onClick = onReading,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) { Text(if (record.isRead) "Ver / corregir lectura" else "REGISTRAR LECTURA", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onAdjust, modifier = Modifier.weight(1f)) { Text("Mover pin", fontSize = 13.sp) }
                OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1f)) { Text("Cómo llegar", fontSize = 13.sp) }
                if (mode == MapMode.DELIVERY) {
                    OutlinedButton(onClick = onReading, modifier = Modifier.weight(1f)) { Text("Lectura", fontSize = 13.sp) }
                } else {
                    OutlinedButton(onClick = onDeleteGps, modifier = Modifier.weight(1f)) { Text("Borrar GPS", fontSize = 13.sp, color = AppColors.Error) }
                }
            }
        }
    }
}

