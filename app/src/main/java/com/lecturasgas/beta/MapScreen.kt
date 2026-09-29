
package com.lecturasgas.beta

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.viewmodel.ReadingViewModel
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

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    focusRecordRowNumber: Int? = null,
    onMarkerClick: (Int) -> Unit = {},
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

    fun createMarkerIcon(tintColor: Int) =
        IconFactory
            .getInstance(context)
            .fromBitmap(
                Bitmap.createBitmap(
                    (context.getDrawable(
                        org.maplibre.android.R.drawable.maplibre_marker_icon_default
                    )!!.intrinsicWidth.takeIf { it > 0 } ?: 48),
                    (context.getDrawable(
                        org.maplibre.android.R.drawable.maplibre_marker_icon_default
                    )!!.intrinsicHeight.takeIf { it > 0 } ?: 48),
                    Bitmap.Config.ARGB_8888
                ).also { bitmap ->
                    val drawable =
                        context.getDrawable(
                            org.maplibre.android.R.drawable.maplibre_marker_icon_default
                        )!!.mutate()

                    drawable.setTint(tintColor)

                    val canvas = Canvas(bitmap)

                    drawable.setBounds(
                        0,
                        0,
                        bitmap.width,
                        bitmap.height
                    )

                    drawable.draw(canvas)
                }
            )

    val greenMarkerIcon = remember {
        createMarkerIcon(Color.GREEN)
    }

    val redMarkerIcon = remember {
        createMarkerIcon(Color.RED)
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
    }

    LaunchedEffect(
        mapInstance,
        mapStyleReady,
        routePoints,
        records,
        focusRecordRowNumber
    ) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!mapStyleReady) return@LaunchedEffect

        map.clear()
        markerRowNumbers.clear()

        map.setOnMarkerClickListener { marker ->
            val rowNumber = markerRowNumbers[marker]

            if (rowNumber != null) {
                selectedMapPoint = routePoints.firstOrNull {
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

            val currentReading = currentRecord?.currentReading

            val markerIcon =
                if (currentReading != null) {
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
                            "Medidor ${point.meter}"
                        )
                        .snippet(
                            "Usuario: ${point.user}\n" +
                                    "Dirección: ${point.address}\n" +
                                    "Barrio: ${point.neighborhood}\n" +
                                    "Lectura: ${currentReading ?: "Pendiente"}"
                        )
                        .icon(markerIcon)
                )

            markerRowNumbers[marker] = point.recordRowNumber
        }

        val focusPoint =
            focusRecordRowNumber?.let { rowNumber ->
                routePoints.firstOrNull {
                    it.recordRowNumber == rowNumber
                }
            }

        if (focusPoint != null && map.locationComponent.isLocationComponentEnabled) {
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
                        .bearing(map.cameraPosition.bearing)
                        .build()
                ),
                700
            )
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

            val centerButton =
                ImageButton(context).apply {

                    background =
                        GradientDrawable().apply {
                            shape =
                                GradientDrawable.OVAL

                            setColor(Color.WHITE)
                        }

                    setImageResource(
                        android.R.drawable.ic_menu_mylocation
                    )

                    scaleType =
                        ImageView.ScaleType.CENTER

                    contentDescription =
                        "Centrar ubicación"

                    setOnClickListener {

                        if (!mapStyleReady) {
                            return@setOnClickListener
                        }

                        val map =
                            mapInstance
                                ?: return@setOnClickListener

                        val component =
                            map.locationComponent

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

            val buttonSize =
                (56 * context.resources.displayMetrics.density)
                    .toInt()

            val margin =
                (16 * context.resources.displayMetrics.density)
                    .toInt()

            val buttonParams =
                FrameLayout.LayoutParams(
                    buttonSize,
                    buttonSize
                ).apply {
                    gravity =
                        Gravity.END or Gravity.BOTTOM

                    setMargins(
                        margin,
                        margin,
                        margin,
                        margin
                    )
                }

            addView(
                centerButton,
                buttonParams
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->

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

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {

            val engine =
                locationEngine

            val callback =
                bearingCallback

            if (engine != null && callback != null) {
                engine.removeLocationUpdates(callback)
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Medidor ${point.meter}",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                    )

                    Text("Usuario: ${point.user}")
                    Text("Dirección: ${point.address}")
                    Text("Barrio: ${point.neighborhood}")

                    val currentRecord =
                        records.firstOrNull {
                            it.rowNumber == point.recordRowNumber
                        }

                    Text(
                        "Lectura: ${currentRecord?.currentReading ?: "Pendiente"}"
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onMarkerClick(point.recordRowNumber)
                            selectedMapPoint = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Registrar lectura")
                    }

                    OutlinedButton(
                        onClick = {
                            pointSettingsOpen = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚙ Configurar punto")
                    }

                    if (pointSettingsOpen) {
                        Spacer(Modifier.height(4.dp))

                        Text(
                            "Configuración de ubicación",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )

                        Button(
                            onClick = { startLocationAdjustment() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ajustar ubicación")
                        }

                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Eliminar ubicación")
                        }
                    }

                    if (editingLocation != null) {
                        Spacer(Modifier.height(4.dp))

                        Text("Ajuste fino de ubicación")
                        Text(
                            "Usa las flechas para mover el punto una pequeña distancia y luego guarda el ajuste."
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { nudgeLocation(0.000001, 0.0) }
                            ) {
                                Text("↑")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { nudgeLocation(0.0, -0.000001) }
                            ) {
                                Text("←")
                            }

                            Button(
                                onClick = { nudgeLocation(0.0, 0.000001) }
                            ) {
                                Text("→")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { nudgeLocation(-0.000001, 0.0) }
                            ) {
                                Text("↓")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { confirmMove = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Guardar")
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedMarker?.position =
                                        LatLng(point.latitude, point.longitude)
                                    editingLocation = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar")
                            }
                        }
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Eliminar ubicación") },
                text = {
                    Text(
                        "¿Confirmas que quieres eliminar la ubicación GPS de este medidor? La lectura y los datos del medidor no se eliminarán."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedMapPoint?.let {
                                vm.deleteRoutePoint(it.recordRowNumber)
                            }
                            confirmDelete = false
                            pointSettingsOpen = false
                            selectedMarker = null
                            selectedMapPoint = null
                        }
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { confirmDelete = false }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (confirmMove) {
            AlertDialog(
                onDismissRequest = { confirmMove = false },
                title = { Text("Guardar nueva ubicación") },
                text = {
                    Text("¿Confirmas que quieres guardar la nueva posición de este punto?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val point = selectedMapPoint
                            val location = editingLocation
                            if (point != null && location != null) {
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
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { confirmMove = false }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

