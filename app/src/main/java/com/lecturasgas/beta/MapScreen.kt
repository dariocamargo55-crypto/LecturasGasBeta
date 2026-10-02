package com.lecturasgas.beta

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.viewmodel.ReadingViewModel
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.Marker
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.TileSet


private fun createDropPinBitmap(
    fillColor: Int,
    selected: Boolean = false
): Bitmap {
    val width = 64
    val height = 76
    val bitmap = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    val centerX = width / 2f

    fun drawPin(pathColor: Int, strokeColor: Int, strokeWidth: Float) {
        val path = android.graphics.Path().apply {
            moveTo(centerX, 70f)
            cubicTo(
                27f,
                64f,
                10f,
                49f,
                10f,
                31f
            )
            cubicTo(
                10f,
                18f,
                20f,
                8f,
                centerX,
                8f
            )
            cubicTo(
                44f,
                8f,
                54f,
                18f,
                54f,
                31f
            )
            cubicTo(
                54f,
                49f,
                37f,
                64f,
                centerX,
                70f
            )
            close()
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pathColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, border)
    }

    drawPin(
        Color.WHITE,
        Color.WHITE,
        2f
    )

    val innerPath = android.graphics.Path().apply {
        moveTo(centerX, 66f)
        cubicTo(
            28f,
            60f,
            14f,
            47f,
            14f,
            31f
        )
        cubicTo(
            14f,
            20f,
            22f,
            12f,
            centerX,
            12f
        )
        cubicTo(
            42f,
            12f,
            50f,
            20f,
            50f,
            31f
        )
        cubicTo(
            50f,
            47f,
            36f,
            60f,
            centerX,
            66f
        )
        close()
    }

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(innerPath, innerPaint)

    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(
        centerX,
        30f,
        9f,
        centerPaint
    )

    val centerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (selected) Color.BLACK else Color.DKGRAY
        style = Paint.Style.FILL
    }
    canvas.drawCircle(
        centerX,
        30f,
        4f,
        centerDot
    )

    return bitmap
}

private fun refreshSatellitePointsOnMap(
    mapInstance: MapLibreMap?,
    satellitePointSourceId: String,
    routePoints: List<com.lecturasgas.beta.data.model.RoutePoint>,
    records: List<com.lecturasgas.beta.data.model.MeterRecord>,
    focusRecordRowNumber: Int?,
    temporaryRowNumber: Int? = null,
    temporaryLocation: LatLng? = null
) {
    val style = mapInstance?.style ?: return

    val source =
        style.getSourceAs<GeoJsonSource>(
            satellitePointSourceId
        ) ?: return

    val featuresJson =
        routePoints.joinToString(
            separator = ",",
            prefix = "{\"type\":\"FeatureCollection\",\"features\":[",
            postfix = "]}"
        ) { point ->
            val isTemporary =
                temporaryRowNumber != null &&
                        temporaryRowNumber == point.recordRowNumber &&
                        temporaryLocation != null

            val latitude =
                if (isTemporary) {
                    temporaryLocation!!.latitude
                } else {
                    point.latitude
                }

            val longitude =
                if (isTemporary) {
                    temporaryLocation!!.longitude
                } else {
                    point.longitude
                }

            val currentReading =
                records.firstOrNull {
                    it.rowNumber == point.recordRowNumber
                }?.currentReading

            val status =
                if (currentReading != null) {
                    "read"
                } else {
                    "pending"
                }

            val focused =
                focusRecordRowNumber != null &&
                        focusRecordRowNumber == point.recordRowNumber

            "{\"type\":\"Feature\",\"properties\":{\"rowNumber\":${point.recordRowNumber},\"status\":\"$status\",\"focused\":$focused},\"geometry\":{\"type\":\"Point\",\"coordinates\":[$longitude,$latitude]}}"
        }

    source.setGeoJson(featuresJson)
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    focusRecordRowNumber: Int? = null,
    onMarkerClick: (Int) -> Unit = {},
    onBackToReadings: () -> Unit = {},
    vm: ReadingViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val routePoints = vm.routePoints
    val records = vm.records

    var selectedMapPoint by remember {
        mutableStateOf<com.lecturasgas.beta.data.model.RoutePoint?>(null)
    }

    var selectedMarker by remember {
        mutableStateOf<Marker?>(null)
    }

    var pointSettingsOpen by remember {
        mutableStateOf(false)
    }

    var editingLocation by remember {
        mutableStateOf<LatLng?>(null)
    }

    var confirmDelete by remember {
        mutableStateOf(false)
    }

    var confirmMove by remember {
        mutableStateOf(false)
    }

    val markerRowNumbers = remember {
        mutableMapOf<Marker, Int>()
    }

    var mapInstance by remember {
        mutableStateOf<MapLibreMap?>(null)
    }

    var mapStyleReady by remember {
        mutableStateOf(false)
    }

    var locationEngine by remember {
        mutableStateOf<LocationEngine?>(null)
    }

    var bearingCallback by remember {
        mutableStateOf<LocationEngineCallback<LocationEngineResult>?>(null)
    }

    var satelliteEnabled by remember {
        mutableStateOf(false)
    }

    val satellitePointSourceId = "lecturas_satellite_points"
    val satellitePendingLayerId = "lecturas_satellite_points_pending"
    val satelliteReadLayerId = "lecturas_satellite_points_read"
    val satelliteFocusLayerId = "lecturas_satellite_points_focus"

    var zoomDisplay by remember {
        mutableStateOf(17.0)
    }

    val greenMarkerIcon = remember {
        IconFactory
            .getInstance(context)
            .fromBitmap(
                createDropPinBitmap(
                    Color.rgb(34, 197, 94)
                )
            )
    }

    val redMarkerIcon = remember {
        IconFactory
            .getInstance(context)
            .fromBitmap(
                createDropPinBitmap(
                    Color.rgb(229, 57, 53)
                )
            )
    }

    val focusMarkerIcon = remember {
        IconFactory
            .getInstance(context)
            .fromBitmap(
                createDropPinBitmap(
                    Color.rgb(255, 214, 0),
                    selected = true
                )
            )
    }

    val mapView = remember {
        MapLibre.getInstance(context)

        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            onCreate(null)

            getMapAsync { map ->
                map.setStyle(
                    "https://tiles.openfreemap.org/styles/liberty"
                ) { style ->
                    mapInstance = map
                    mapStyleReady = true

                    map.setMaxZoomPreference(22.0)

                    val satelliteSourceId =
                        "lecturas_satellite_source"

                    val satelliteLayerId =
                        "lecturas_satellite_layer"

                    if (style.getSource(satelliteSourceId) == null) {
                        val satelliteTileSet =
                            TileSet(
                                "2.2.0",
                                "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                            ).apply {
                                scheme = "xyz"
                                minZoom = 0f
                                maxZoom = 23f
                            }

                        val satelliteSource =
                            RasterSource(
                                satelliteSourceId,
                                satelliteTileSet,
                                256
                            )

                        style.addSource(satelliteSource)

                        val satelliteLayer =
                            RasterLayer(
                                satelliteLayerId,
                                satelliteSourceId
                            ).withProperties(
                                PropertyFactory.visibility(
                                    Property.NONE
                                ),
                                PropertyFactory.rasterOpacity(
                                    1.0f
                                ),
                                PropertyFactory.rasterSaturation(
                                    0.05f
                                )
                            )

                        // La imagen satelital queda como capa de fondo.
                        style.addLayer(satelliteLayer)

                        if (style.getSource(satellitePointSourceId) == null) {
                            style.addSource(
                                GeoJsonSource(
                                    satellitePointSourceId,
                                    "{\"type\":\"FeatureCollection\",\"features\":[]}"
                                )
                            )

                            if (style.getImage("lecturas_pin_pending") == null) {
                                style.addImage(
                                    "lecturas_pin_pending",
                                    createDropPinBitmap(
                                        Color.rgb(229, 57, 53)
                                    )
                                )
                            }

                            if (style.getImage("lecturas_pin_read") == null) {
                                style.addImage(
                                    "lecturas_pin_read",
                                    createDropPinBitmap(
                                        Color.rgb(34, 197, 94)
                                    )
                                )
                            }

                            if (style.getImage("lecturas_pin_focus") == null) {
                                style.addImage(
                                    "lecturas_pin_focus",
                                    createDropPinBitmap(
                                        Color.rgb(255, 214, 0),
                                        selected = true
                                    )
                                )
                            }

                            val pendingLayer =
                                SymbolLayer(
                                    satellitePendingLayerId,
                                    satellitePointSourceId
                                ).withFilter(
                                    Expression.all(
                                        Expression.eq(
                                            Expression.get("status"),
                                            "pending"
                                        ),
                                        Expression.eq(
                                            Expression.get("focused"),
                                            false
                                        )
                                    )
                                ).withProperties(
                                    PropertyFactory.iconImage(
                                        "lecturas_pin_pending"
                                    ),
                                    PropertyFactory.iconAnchor(
                                        Property.ICON_ANCHOR_BOTTOM
                                    ),
                                    PropertyFactory.iconAllowOverlap(true),
                                    PropertyFactory.iconIgnorePlacement(true),
                                    PropertyFactory.iconSize(0.72f)
                                )

                            val readLayer =
                                SymbolLayer(
                                    satelliteReadLayerId,
                                    satellitePointSourceId
                                ).withFilter(
                                    Expression.all(
                                        Expression.eq(
                                            Expression.get("status"),
                                            "read"
                                        ),
                                        Expression.eq(
                                            Expression.get("focused"),
                                            false
                                        )
                                    )
                                ).withProperties(
                                    PropertyFactory.iconImage(
                                        "lecturas_pin_read"
                                    ),
                                    PropertyFactory.iconAnchor(
                                        Property.ICON_ANCHOR_BOTTOM
                                    ),
                                    PropertyFactory.iconAllowOverlap(true),
                                    PropertyFactory.iconIgnorePlacement(true),
                                    PropertyFactory.iconSize(0.72f)
                                )

                            val focusLayer =
                                SymbolLayer(
                                    satelliteFocusLayerId,
                                    satellitePointSourceId
                                ).withFilter(
                                    Expression.eq(
                                        Expression.get("focused"),
                                        true
                                    )
                                ).withProperties(
                                    PropertyFactory.iconImage(
                                        "lecturas_pin_focus"
                                    ),
                                    PropertyFactory.iconAnchor(
                                        Property.ICON_ANCHOR_BOTTOM
                                    ),
                                    PropertyFactory.iconAllowOverlap(true),
                                    PropertyFactory.iconIgnorePlacement(true),
                                    PropertyFactory.iconSize(0.84f)
                                )

                            style.addLayer(pendingLayer)
                            style.addLayer(readLayer)
                            style.addLayer(focusLayer)
                        }
                    }

                    val locationComponentOptions =
                        LocationComponentOptions
                            .builder(context)
                            .pulseEnabled(true)
                            .build()

                    val locationEngineRequest =
                        LocationEngineRequest.Builder(
                            1000L
                        )
                            .setFastestInterval(500L)
                            .setPriority(
                                LocationEngineRequest.PRIORITY_HIGH_ACCURACY
                            )
                            .build()

                    val hasLocationPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                    if (!hasLocationPermission) {
                        return@setStyle
                    }

                    val locationComponentActivationOptions =
                        LocationComponentActivationOptions
                            .builder(context, style)
                            .locationComponentOptions(
                                locationComponentOptions
                            )
                            .locationEngineRequest(
                                locationEngineRequest
                            )
                            .useDefaultLocationEngine(true)
                            .build()

                    val locationComponent =
                        map.locationComponent

                    locationComponent.activateLocationComponent(
                        locationComponentActivationOptions
                    )

                    locationComponent.isLocationComponentEnabled = true

                    locationComponent.setCameraMode(
                        CameraMode.TRACKING,
                        1000L,
                        17.0,
                        null,
                        null,
                        null
                    )

                    val engine =
                        locationComponent.locationEngine

                    locationEngine = engine

                    var lastBearing = 0.0

                    val callback =
                        object :
                            LocationEngineCallback<LocationEngineResult> {

                            override fun onSuccess(
                                result: LocationEngineResult?
                            ) {
                                val location =
                                    result?.lastLocation
                                        ?: return

                                if (!location.hasBearing()) {
                                    return
                                }

                                if (!location.hasSpeed()) {
                                    return
                                }

                                if (location.speed < 0.7f) {
                                    return
                                }

                                val targetBearing =
                                    location.bearing.toDouble()

                                var difference =
                                    targetBearing - lastBearing

                                while (difference > 180.0) {
                                    difference -= 360.0
                                }

                                while (difference < -180.0) {
                                    difference += 360.0
                                }

                                lastBearing +=
                                    difference * 0.35

                                if (lastBearing < 0.0) {
                                    lastBearing += 360.0
                                }

                                if (lastBearing >= 360.0) {
                                    lastBearing -= 360.0
                                }

                                map.easeCamera(
                                    CameraUpdateFactory.bearingTo(
                                        lastBearing
                                    ),
                                    350
                                )
                            }

                            override fun onFailure(
                                exception: Exception
                            ) {
                                // Sin acción.
                            }
                        }

                    bearingCallback = callback

                    engine?.requestLocationUpdates(
                        locationEngineRequest,
                        callback,
                        Looper.getMainLooper()
                    )
                }
            }
        }
    }

    fun startLocationAdjustment() {
        val point = selectedMapPoint ?: return
        editingLocation = LatLng(point.latitude, point.longitude)
        pointSettingsOpen = false
    }

    fun nudgeLocation(deltaLat: Double, deltaLon: Double) {
        val current = editingLocation ?: return

        val updated = LatLng(
            current.latitude + deltaLat,
            current.longitude + deltaLon
        )

        editingLocation = updated
        selectedMarker?.position = updated

        if (selectedMarker == null) {
            refreshSatellitePointsOnMap(
                mapInstance = mapInstance,
                satellitePointSourceId = satellitePointSourceId,
                routePoints = routePoints,
                records = records,
                focusRecordRowNumber = focusRecordRowNumber,
                temporaryRowNumber = selectedMapPoint?.recordRowNumber,
                temporaryLocation = updated
            )
        }
    }

    LaunchedEffect(
        mapInstance,
        mapStyleReady,
        routePoints,
        records,
        focusRecordRowNumber,
        satelliteEnabled
    ) {
        val map = mapInstance ?: return@LaunchedEffect

        if (!mapStyleReady) {
            return@LaunchedEffect
        }

        refreshSatellitePointsOnMap(
            mapInstance = mapInstance,
            satellitePointSourceId = satellitePointSourceId,
            routePoints = routePoints,
            records = records,
            focusRecordRowNumber = focusRecordRowNumber
        )

        map.clear()
        markerRowNumbers.clear()

        map.setOnMarkerClickListener { marker ->

            val rowNumber =
                markerRowNumbers[marker]

            if (rowNumber != null) {

                selectedMapPoint =
                    routePoints.firstOrNull {
                        it.recordRowNumber == rowNumber
                    }

                selectedMarker = marker
                pointSettingsOpen = false
                editingLocation = null

                true
            } else {
                false
            }
        }

        routePoints.forEach { point ->

            val currentRecord =
                records.firstOrNull {
                    it.rowNumber == point.recordRowNumber
                }

            val currentReading =
                currentRecord?.currentReading

            val isFocusedPoint =
                focusRecordRowNumber != null &&
                        point.recordRowNumber ==
                        focusRecordRowNumber

            val markerIcon =
                if (isFocusedPoint) {
                    focusMarkerIcon
                } else if (currentReading != null) {
                    greenMarkerIcon
                } else {
                    redMarkerIcon
                }

            val marker =
                map.addMarker(
                    MarkerOptions()
                        .position(
                            LatLng(
                                point.latitude,
                                point.longitude
                            )
                        )
                        .title(
                            if (isFocusedPoint) {
                                "📍 MEDIDOR SELECCIONADO: ${point.meter}"
                            } else {
                                "Medidor ${point.meter}"
                            }
                        )
                        .snippet(
                            "Usuario: ${point.user}\n" +
                                    "Dirección: ${point.address}\n" +
                                    "Barrio: ${point.neighborhood}\n" +
                                    "Lectura: ${currentReading ?: "Pendiente"}"
                        )
                        .icon(markerIcon)
                )

            markerRowNumbers[marker] =
                point.recordRowNumber
        }

        val focusPoint =
            focusRecordRowNumber?.let { rowNumber ->

                routePoints.firstOrNull {
                    it.recordRowNumber == rowNumber
                }
            }

        if (focusPoint != null) {

            val target =
                LatLng(
                    focusPoint.latitude,
                    focusPoint.longitude
                )

            map.locationComponent.setCameraMode(
                CameraMode.NONE
            )

            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(18.0)
                        .bearing(
                            map.cameraPosition.bearing
                        )
                        .build()
                ),
                700
            )
        }
    }

    LaunchedEffect(
        mapInstance,
        mapStyleReady,
        satelliteEnabled
    ) {
        val map = mapInstance ?: return@LaunchedEffect

        if (!mapStyleReady) {
            return@LaunchedEffect
        }

        val style = map.style ?: return@LaunchedEffect

        val satelliteLayer =
            style.getLayer("lecturas_satellite_layer")
                ?: return@LaunchedEffect

        satelliteLayer.setProperties(
            PropertyFactory.visibility(
                if (satelliteEnabled) {
                    Property.VISIBLE
                } else {
                    Property.NONE
                }
            )
        )

        val pointVisibility =
            if (satelliteEnabled) {
                Property.VISIBLE
            } else {
                Property.NONE
            }

        listOf(
            satellitePendingLayerId,
            satelliteReadLayerId,
            satelliteFocusLayerId
        ).forEach { layerId ->
            style.getLayer(layerId)?.setProperties(
                PropertyFactory.visibility(
                    pointVisibility
                )
            )
        }

        refreshSatellitePointsOnMap(
            mapInstance = mapInstance,
            satellitePointSourceId = satellitePointSourceId,
            routePoints = routePoints,
            records = records,
            focusRecordRowNumber = focusRecordRowNumber
        )
    }

    DisposableEffect(
        mapInstance,
        mapStyleReady,
        satelliteEnabled,
        routePoints,
        records,
        focusRecordRowNumber
    ) {
        val map = mapInstance

        if (map == null || !mapStyleReady || !satelliteEnabled) {
            onDispose { }
        } else {
            val listener =
                MapLibreMap.OnMapClickListener { point ->
                    val screenPoint =
                        map.projection.toScreenLocation(point)

                    val selected =
                        routePoints
                            .map { routePoint ->
                                val routeScreenPoint =
                                    map.projection.toScreenLocation(
                                        LatLng(
                                            routePoint.latitude,
                                            routePoint.longitude
                                        )
                                    )

                                val dx =
                                    routeScreenPoint.x - screenPoint.x

                                val dy =
                                    routeScreenPoint.y - screenPoint.y

                                val distanceSquared =
                                    dx * dx + dy * dy

                                routePoint to distanceSquared
                            }
                            .minByOrNull { it.second }
                            ?.takeIf { it.second <= 45f * 45f }
                            ?.first

                    if (selected != null) {
                        selectedMapPoint = selected
                        selectedMarker = null
                        pointSettingsOpen = false
                        editingLocation = null
                        true
                    } else {
                        false
                    }
                }

            map.addOnMapClickListener(listener)

            onDispose {
                map.removeOnMapClickListener(listener)
            }
        }
    }

    val mapContainer = remember(mapView) {

        FrameLayout(context).apply {

            addView(
                mapView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            fun createGlassBackground(
                cornerRadiusDp: Float = 16f,
                color: Int = Color.argb(222, 255, 255, 255)
            ): GradientDrawable {
                val density = context.resources.displayMetrics.density
                return GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerRadiusDp * density
                    setColor(color)
                    setStroke(
                        (1 * density).toInt(),
                        Color.argb(90, 255, 255, 255)
                    )
                }
            }

            fun createGlassButton(
                text: String,
                textSize: Float,
                contentDescription: String
            ): TextView {
                return TextView(context).apply {
                    this.text = text
                    this.textSize = textSize
                    setTextColor(Color.rgb(28, 36, 46))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    this.contentDescription = contentDescription
                    background = createGlassBackground(
                        cornerRadiusDp = 15f
                    )
                    elevation =
                        5 * context.resources.displayMetrics.density
                    isClickable = true
                    isFocusable = true
                }
            }

            val zoomPlus =
                createGlassButton(
                    "+",
                    23f,
                    "Acercar mapa"
                )

            val zoomMinus =
                createGlassButton(
                    "−",
                    23f,
                    "Alejar mapa"
                )

            zoomPlus.setOnClickListener {
                val map = mapInstance ?: return@setOnClickListener
                map.animateCamera(
                    CameraUpdateFactory.zoomIn(),
                    220
                )
            }

            zoomMinus.setOnClickListener {
                val map = mapInstance ?: return@setOnClickListener
                map.animateCamera(
                    CameraUpdateFactory.zoomOut(),
                    220
                )
            }

            val satelliteAttribution =
                createGlassButton(
                    "© Esri",
                    10f,
                    "Atribución de imágenes satelitales"
                ).apply {
                    setPadding(
                        8,
                        3,
                        8,
                        3
                    )
                }

            val satelliteButton =
                createGlassButton(
                    if (satelliteEnabled) "MAP" else "SAT",
                    13f,
                    "Cambiar entre mapa y satélite"
                ).apply {
                    minWidth =
                        (62 * context.resources.displayMetrics.density)
                            .toInt()
                    minHeight =
                        (44 * context.resources.displayMetrics.density)
                            .toInt()
                }

            fun updateSatelliteButtonAppearance() {
                satelliteButton.text =
                    if (satelliteEnabled) "MAP" else "SAT"

                satelliteButton.setTextColor(
                    if (satelliteEnabled) {
                        Color.WHITE
                    } else {
                        Color.rgb(28, 36, 46)
                    }
                )

                satelliteButton.background =
                    createGlassBackground(
                        cornerRadiusDp = 15f,
                        color =
                            if (satelliteEnabled) {
                                Color.argb(220, 30, 41, 59)
                            } else {
                                Color.argb(222, 255, 255, 255)
                            }
                    )
            }

            satelliteButton.setOnClickListener {
                satelliteEnabled = !satelliteEnabled

                // El mapa vectorial admite un acercamiento mayor.
                // En satélite limitamos el zoom al nivel donde la cobertura
                // de World Imagery es más consistente para evitar el mensaje
                // de datos no disponibles.
                mapInstance?.setMaxZoomPreference(
                    if (satelliteEnabled) 19.0 else 22.0
                )

                updateSatelliteButtonAppearance()

                satelliteAttribution.visibility =
                    if (satelliteEnabled) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
            }

            updateSatelliteButtonAppearance()
            satelliteAttribution.visibility =
                if (satelliteEnabled) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }

            val centerButton =
                ImageButton(context).apply {
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(
                                Color.argb(
                                    222,
                                    255,
                                    255,
                                    255
                                )
                            )
                            setStroke(
                                (
                                        1 * context.resources
                                            .displayMetrics.density
                                        ).toInt(),
                                Color.argb(90, 255, 255, 255)
                            )
                        }

                    setImageResource(
                        android.R.drawable.ic_menu_mylocation
                    )

                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = "Centrar ubicación"
                    elevation =
                        5 * context.resources.displayMetrics.density

                    setOnClickListener {
                        if (!mapStyleReady) {
                            return@setOnClickListener
                        }

                        val map =
                            mapInstance
                                ?: return@setOnClickListener

                        val component = map.locationComponent

                        if (!component.isLocationComponentEnabled) {
                            return@setOnClickListener
                        }

                        val location =
                            component.lastKnownLocation
                                ?: return@setOnClickListener

                        val currentBearing =
                            map.cameraPosition.bearing

                        val cameraPosition =
                            CameraPosition.Builder()
                                .target(
                                    LatLng(
                                        location.latitude,
                                        location.longitude
                                    )
                                )
                                .zoom(17.0)
                                .bearing(currentBearing)
                                .build()

                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                cameraPosition
                            ),
                            600
                        )

                        component.setCameraMode(
                            CameraMode.TRACKING,
                            600L,
                            17.0,
                            currentBearing,
                            0.0,
                            null
                        )
                    }
                }

            val density =
                context.resources.displayMetrics.density

            val smallButtonSize =
                (48 * density).toInt()

            val centerButtonSize =
                (52 * density).toInt()

            val margin =
                (14 * density).toInt()

            val gap =
                (8 * density).toInt()

            val zoomPlusParams =
                FrameLayout.LayoutParams(
                    smallButtonSize,
                    smallButtonSize
                ).apply {
                    gravity = Gravity.END or Gravity.BOTTOM
                    setMargins(
                        margin,
                        margin,
                        margin,
                        margin + centerButtonSize + gap
                    )
                }

            val zoomMinusParams =
                FrameLayout.LayoutParams(
                    smallButtonSize,
                    smallButtonSize
                ).apply {
                    gravity = Gravity.END or Gravity.BOTTOM
                    setMargins(
                        margin,
                        margin,
                        margin,
                        margin + centerButtonSize + gap + smallButtonSize + gap
                    )
                }

            val centerButtonParams =
                FrameLayout.LayoutParams(
                    centerButtonSize,
                    centerButtonSize
                ).apply {
                    gravity = Gravity.END or Gravity.BOTTOM
                    setMargins(
                        margin,
                        margin,
                        margin,
                        margin
                    )
                }

            val satelliteButtonParams =
                FrameLayout.LayoutParams(
                    (62 * density).toInt(),
                    (44 * density).toInt()
                ).apply {
                    gravity = Gravity.END or Gravity.TOP
                    setMargins(
                        margin,
                        margin,
                        margin,
                        margin
                    )
                }

            val satelliteAttributionParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    (28 * density).toInt()
                ).apply {
                    gravity = Gravity.START or Gravity.BOTTOM
                    setMargins(
                        margin,
                        margin,
                        margin,
                        margin
                    )
                }

            addView(
                satelliteButton,
                satelliteButtonParams
            )

            addView(
                satelliteAttribution,
                satelliteAttributionParams
            )

            addView(
                zoomPlus,
                zoomPlusParams
            )

            addView(
                zoomMinus,
                zoomMinusParams
            )

            addView(
                centerButton,
                centerButtonParams
            )
        }
    }

    DisposableEffect(
        lifecycleOwner,
        mapView
    ) {

        val observer =
            androidx.lifecycle.LifecycleEventObserver {
                    _,
                    event ->

                when (event) {

                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        mapView.onStart()
                    }

                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        mapView.onResume()
                    }

                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                        mapView.onPause()
                    }

                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        mapView.onStop()
                    }

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(
            observer
        )

        onDispose {

            val engine =
                locationEngine

            val callback =
                bearingCallback

            if (
                engine != null &&
                callback != null
            ) {
                engine.removeLocationUpdates(
                    callback
                )
            }

            lifecycleOwner.lifecycle.removeObserver(
                observer
            )

            mapView.onDestroy()
        }
    }

    Box(
        modifier = modifier
    ) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapContainer
            }
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clickable {
                    onBackToReadings()
                },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.86f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = "‹",
                modifier = Modifier.padding(
                    horizontal = 15.dp,
                    vertical = 5.dp
                ),
                fontSize = 30.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Light
            )
        }

        selectedMapPoint?.let { point ->

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors()
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {

                    Text(
                        text =
                            "Medidor ${point.meter}",
                        style =
                            androidx.compose.material3
                                .MaterialTheme
                                .typography
                                .titleLarge
                    )

                    Text(
                        "Usuario: ${point.user}"
                    )

                    Text(
                        "Dirección: ${point.address}"
                    )

                    Text(
                        "Barrio: ${point.neighborhood}"
                    )

                    val currentRecord =
                        records.firstOrNull {
                            it.rowNumber ==
                                    point.recordRowNumber
                        }

                    Text(
                        "Lectura: ${
                            currentRecord?.currentReading
                                ?: "Pendiente"
                        }"
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            onMarkerClick(
                                point.recordRowNumber
                            )

                            selectedMapPoint = null
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Registrar lectura"
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            pointSettingsOpen = true
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "⚙ Configurar punto"
                        )
                    }

                    if (pointSettingsOpen) {

                        Spacer(
                            Modifier.height(4.dp)
                        )

                        Text(
                            "Configuración de ubicación",
                            style =
                                androidx.compose.material3
                                    .MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Button(
                            onClick = {
                                startLocationAdjustment()
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Ajustar ubicación"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                confirmDelete = true
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Eliminar ubicación"
                            )
                        }
                    }

                    if (editingLocation != null) {

                        Spacer(
                            Modifier.height(4.dp)
                        )

                        Text(
                            "Ajuste fino de ubicación"
                        )

                        Text(
                            "Usa las flechas para mover el punto una pequeña distancia y luego guarda el ajuste."
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.Center
                        ) {

                            Button(
                                onClick = {
                                    nudgeLocation(
                                        0.000001,
                                        0.0
                                    )
                                }
                            ) {
                                Text("↑")
                            }
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceEvenly
                        ) {

                            Button(
                                onClick = {
                                    nudgeLocation(
                                        0.0,
                                        -0.000001
                                    )
                                }
                            ) {
                                Text("←")
                            }

                            Button(
                                onClick = {
                                    nudgeLocation(
                                        0.0,
                                        0.000001
                                    )
                                }
                            ) {
                                Text("→")
                            }
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.Center
                        ) {

                            Button(
                                onClick = {
                                    nudgeLocation(
                                        -0.000001,
                                        0.0
                                    )
                                }
                            ) {
                                Text("↓")
                            }
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {

                            Button(
                                onClick = {
                                    confirmMove = true
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    "Guardar"
                                )
                            }

                            OutlinedButton(
                                onClick = {

                                    selectedMarker?.position =
                                        LatLng(
                                            point.latitude,
                                            point.longitude
                                        )

                                    refreshSatellitePointsOnMap(
                                        mapInstance = mapInstance,
                                        satellitePointSourceId = satellitePointSourceId,
                                        routePoints = routePoints,
                                        records = records,
                                        focusRecordRowNumber = focusRecordRowNumber
                                    )

                                    editingLocation = null
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    "Cancelar"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (confirmDelete) {

            AlertDialog(
                onDismissRequest = {
                    confirmDelete = false
                },

                title = {
                    Text(
                        "Eliminar ubicación"
                    )
                },

                text = {
                    Text(
                        "¿Confirmas que quieres eliminar la ubicación GPS de este medidor? La lectura y los datos del medidor no se eliminarán."
                    )
                },

                confirmButton = {

                    Button(
                        onClick = {

                            selectedMapPoint?.let {
                                vm.deleteRoutePoint(
                                    it.recordRowNumber
                                )
                            }

                            confirmDelete = false
                            pointSettingsOpen = false
                            selectedMarker = null
                            selectedMapPoint = null
                        }
                    ) {
                        Text(
                            "Eliminar"
                        )
                    }
                },

                dismissButton = {

                    OutlinedButton(
                        onClick = {
                            confirmDelete = false
                        }
                    ) {
                        Text(
                            "Cancelar"
                        )
                    }
                }
            )
        }

        if (confirmMove) {

            AlertDialog(
                onDismissRequest = {
                    confirmMove = false
                },

                title = {
                    Text(
                        "Guardar nueva ubicación"
                    )
                },

                text = {
                    Text(
                        "¿Confirmas que quieres guardar la nueva posición de este punto?"
                    )
                },

                confirmButton = {

                    Button(
                        onClick = {

                            val point =
                                selectedMapPoint

                            val location =
                                editingLocation

                            if (
                                point != null &&
                                location != null
                            ) {

                                vm.updateRoutePointLocation(
                                    point.recordRowNumber,
                                    location.latitude,
                                    location.longitude
                                )
                            }

                            confirmMove = false
                            editingLocation = null
                        }
                    ) {
                        Text(
                            "Guardar"
                        )
                    }
                },

                dismissButton = {

                    OutlinedButton(
                        onClick = {
                            confirmMove = false
                        }
                    ) {
                        Text(
                            "Cancelar"
                        )
                    }
                }
            )
        }
    }
}