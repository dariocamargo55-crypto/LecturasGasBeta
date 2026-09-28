package com.lecturasgas.beta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.ui.MeterRecordCard
import com.lecturasgas.beta.ui.ReadingDialog
import com.lecturasgas.beta.ui.RouteSegmentCard
import com.lecturasgas.beta.viewmodel.ReadingViewModel
import com.lecturasgas.beta.viewmodel.SearchMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    vm: ReadingViewModel = viewModel()
) {

    var selectedRecord by remember {
        mutableStateOf<MeterRecord?>(null)
    }

    var menuExpanded by remember {
        mutableStateOf(false)
    }

    var keyboardMenuExpanded by remember {
        mutableStateOf(false)
    }

    var searchModeMenuExpanded by remember {
        mutableStateOf(false)
    }

    var expandedSegmentId by remember {
        mutableStateOf(vm.lastExpandedSegmentId)
    }

    // Durante una búsqueda permitimos mantener abiertas varias tarjetas
    // de barrio al mismo tiempo para comparar resultados.
    var expandedSearchSegmentIds by remember {
        mutableStateOf<Set<Int>>(emptySet())
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current

    var pendingStartRoute by remember {
        mutableStateOf(false)
    }

    var showMap by remember {
        mutableStateOf(false)
    }

    var mapFocusRowNumber by remember {
        mutableStateOf<Int?>(null)
    }

    val hasLocationPermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted && pendingStartRoute) {
                vm.startRouteRecording()
            } else if (pendingStartRoute) {
                vm.clearMessage()
                vm.updateCurrentLocation(0.0, 0.0, null)
            }

            pendingStartRoute = false
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                vm.importExcel(uri)
                expandedSegmentId = null
                expandedSearchSegmentIds = emptySet()
                vm.updateExpandedSegment(null)
            }
        }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        ) { uri ->

            if (uri != null) {
                vm.exportExcel(uri)
            }
        }

    DisposableEffect(
        vm.routeRecordingActive,
        hasLocationPermission
    ) {
        if (vm.routeRecordingActive && hasLocationPermission) {
            val locationManager =
                context.getSystemService(
                    Context.LOCATION_SERVICE
                ) as LocationManager

            val listener = object : LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    vm.updateCurrentLocation(
                        location.latitude,
                        location.longitude,
                        location.accuracy
                    )
                }

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit

                @Suppress("DEPRECATION")
                override fun onStatusChanged(
                    provider: String?,
                    status: Int,
                    extras: Bundle?
                ) = Unit
            }

            val providers =
                listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER
                )

            providers.forEach { provider ->
                runCatching {
                    val last =
                        locationManager.getLastKnownLocation(provider)

                    if (last != null) {
                        vm.updateCurrentLocation(
                            last.latitude,
                            last.longitude,
                            last.accuracy
                        )
                    }
                }
            }

            providers.forEach { provider ->
                runCatching {
                    locationManager.requestLocationUpdates(
                        provider,
                        2000L,
                        3f,
                        listener
                    )
                }
            }

            onDispose {
                runCatching {
                    locationManager.removeUpdates(listener)
                }
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(vm.message) {

        if (vm.message != null) {

            kotlinx.coroutines.delay(2500)

            vm.clearMessage()
        }
    }

    LaunchedEffect(vm.search) {
        if (vm.search.isBlank()) {
            expandedSearchSegmentIds = emptySet()
        }
    }

    LaunchedEffect(
        expandedSegmentId,
        vm.search
    ) {
        val id = expandedSegmentId

        if (id != null && vm.search.isBlank()) {
            val segmentIndex =
                vm.visibleRouteSegments
                    .indexOfFirst { it.id == id }

            if (segmentIndex >= 0) {
                listState.animateScrollToItem(
                    segmentIndex * 2
                )
            }
        }
    }

    val targetProgress =
        if (vm.records.isEmpty()) {
            0f
        } else {
            vm.readCount.toFloat() /
                    vm.records.size.toFloat()
        }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "routeProgress"
    )

    val uniqueNeighborhoods =
        remember(vm.records) {
            vm.records
                .asSequence()
                .map {
                    it.neighborhood
                        .trim()
                        .uppercase()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()
                .count()
        }

    MaterialTheme {

        Scaffold(

            topBar = {

                TopAppBar(

                    title = {
                        Text(
                            "Lecturas Gas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },

                    actions = {

                        IconButton(
                            onClick = {
                                menuExpanded = true
                            }
                        ) {

                            Text(
                                text = "⋮",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                            }
                        ) {

                            DropdownMenuItem(
                                leadingIcon = {
                                    Text(
                                        if (vm.routeRecordingActive) "■" else "▶",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text(
                                        if (vm.routeRecordingActive) {
                                            "Finalizar ruta GPS"
                                        } else {
                                            "Iniciar ruta GPS"
                                        }
                                    )
                                },
                                onClick = {
                                    menuExpanded = false

                                    if (vm.routeRecordingActive) {
                                        vm.stopRouteRecording()
                                    } else if (hasLocationPermission) {
                                        vm.startRouteRecording()
                                    } else {
                                        pendingStartRoute = true
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = vm.imported && !vm.isBusy
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Text(
                                        "⌖",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                text = {
                                    Text(
                                        if (showMap) {
                                            "Volver a lecturas"
                                        } else {
                                            "Abrir mapa"
                                        }
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    showMap = !showMap
                                },
                                enabled = vm.imported
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Text(
                                        "↓",
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text("Importar Excel")
                                },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                enabled = !vm.isBusy
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Text(
                                        "↑",
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text("Exportar Excel")
                                },
                                onClick = {
                                    menuExpanded = false
                                    exportLauncher.launch(
                                        "Lecturas_A_terminado.xlsx"
                                    )
                                },
                                enabled = vm.imported && !vm.isBusy
                            )
                        }
                    }
                )
            }

        ) { paddingValues ->

            if (showMap) {
                MapScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    focusRecordRowNumber = mapFocusRowNumber,
                    onMarkerClick = { rowNumber ->
                        val record =
                            vm.records.firstOrNull {
                                it.rowNumber == rowNumber
                            }

                        if (record != null) {
                            selectedRecord = record
                            showMap = false
                            mapFocusRowNumber = null
                        }
                    }
                )
            } else {
                Column(

                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(
                                horizontal = 12.dp
                            )

                ) {

                    // -------------------------------------------------------------
                    // DASHBOARD DE LA RUTA
                    // -------------------------------------------------------------

                    if (vm.imported) {

                        Spacer(Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec = tween(durationMillis = 300)
                                ),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFFF6A00),
                                                Color(0xFFF02D3A)
                                            )
                                        )
                                    )
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.lecturas_gas_logo),
                                        contentDescription = "Lecturas Gas",
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "RUTA ACTUAL",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.82f),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = vm.routeName ?: "Ruta sin nombre",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "${vm.records.size} medidores · $uniqueNeighborhoods barrios",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.92f)
                                        )
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clickable { showMap = true },
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.90f)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "⌖",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD92F3A)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Column(modifier = Modifier.padding(11.dp)) {
                                            Text(
                                                text = "${vm.readCount}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Leídas",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.88f)
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Column(modifier = Modifier.padding(11.dp)) {
                                            Text(
                                                text = "${vm.pendingCount}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Pendientes",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.88f)
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Column(modifier = Modifier.padding(11.dp)) {
                                            Text(
                                                text = "${vm.routePointCount}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Ubicaciones",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.88f)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(13.dp))

                                LinearProgressIndicator(
                                    progress = animatedProgress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp),
                                    color = Color(0xFF45D483),
                                    trackColor = Color.White.copy(alpha = 0.30f)
                                )

                                Spacer(Modifier.height(13.dp))

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .clickable(enabled = !vm.isBusy) {
                                            if (vm.routeRecordingActive) {
                                                vm.stopRouteRecording()
                                            } else if (hasLocationPermission) {
                                                vm.startRouteRecording()
                                            } else {
                                                pendingStartRoute = true
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        },
                                    shape = RoundedCornerShape(25.dp),
                                    color = Color.White,
                                    contentColor = Color(0xFF252936)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (vm.routeRecordingActive) "■" else "▶",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD92F3A)
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Text(
                                            text = if (vm.routeRecordingActive) {
                                                "Finalizar ruta GPS"
                                            } else {
                                                "Iniciar ruta GPS"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                    } else {

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Importa el Excel de la ruta para comenzar.",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(Modifier.height(12.dp))
                    }

                    // -------------------------------------------------------------
                    // BARRA DE BÚSQUEDA
                    // -------------------------------------------------------------

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Buscar por",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.weight(1f))

                            Box {
                                Surface(
                                    modifier = Modifier.clickable {
                                        searchModeMenuExpanded = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${vm.searchMode.label} ▾",
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                DropdownMenu(
                                    expanded = searchModeMenuExpanded,
                                    onDismissRequest = {
                                        searchModeMenuExpanded = false
                                    }
                                ) {
                                    SearchMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label) },
                                            onClick = {
                                                vm.updateSearchMode(mode)
                                                searchModeMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            OutlinedTextField(

                                value =
                                    vm.search,

                                onValueChange = {
                                    vm.updateSearch(it)
                                },

                                modifier =
                                    Modifier.weight(1f),

                                placeholder = {

                                    Text(
                                        "Buscar..."
                                    )
                                },

                                leadingIcon = {

                                    Text(
                                        text = "⌕",
                                        fontSize = 30.sp,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant
                                    )
                                },

                                trailingIcon = {

                                    Box(
                                        contentAlignment =
                                            Alignment.Center
                                    ) {

                                        Surface(
                                            modifier =
                                                Modifier
                                                    .padding(
                                                        end = 6.dp
                                                    )
                                                    .clickable {
                                                        keyboardMenuExpanded =
                                                            true
                                                    },
                                            shape =
                                                RoundedCornerShape(
                                                    10.dp
                                                ),
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .surfaceVariant
                                        ) {

                                            Text(

                                                text =
                                                    if (
                                                        vm.numericKeyboard
                                                    ) {
                                                        "123"
                                                    } else {
                                                        "ABC"
                                                    },

                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 9.dp,
                                                        vertical = 5.dp
                                                    ),

                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .labelLarge,

                                                fontWeight =
                                                    FontWeight.SemiBold
                                            )
                                        }

                                        DropdownMenu(

                                            expanded =
                                                keyboardMenuExpanded,

                                            onDismissRequest = {
                                                keyboardMenuExpanded =
                                                    false
                                            }

                                        ) {

                                            DropdownMenuItem(

                                                text = {
                                                    Text(
                                                        "123  Teclado numérico"
                                                    )
                                                },

                                                onClick = {

                                                    vm.updateNumericKeyboard(
                                                        true
                                                    )

                                                    keyboardMenuExpanded =
                                                        false
                                                }
                                            )

                                            DropdownMenuItem(

                                                text = {
                                                    Text(
                                                        "ABC  Teclado normal"
                                                    )
                                                },

                                                onClick = {

                                                    vm.updateNumericKeyboard(
                                                        false
                                                    )

                                                    keyboardMenuExpanded =
                                                        false
                                                }
                                            )
                                        }
                                    }
                                },

                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType =
                                            if (vm.numericKeyboard) {
                                                KeyboardType.Number
                                            } else {
                                                KeyboardType.Text
                                            }
                                    ),

                                singleLine = true,

                                shape =
                                    RoundedCornerShape(
                                        18.dp
                                    )
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                    // -------------------------------------------------------------
                    // ENCABEZADO DE BARRIOS
                    // -------------------------------------------------------------

                    if (vm.imported) {

                        Text(

                            text =
                                if (
                                    vm.search.isBlank()
                                ) {
                                    "Barrios de la ruta"
                                } else {
                                    "Resultados"
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,

                            fontWeight =
                                FontWeight.SemiBold,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )
                    }

                    // -------------------------------------------------------------
                    // LISTA DE BARRIOS
                    // -------------------------------------------------------------

                    LazyColumn(

                        state = listState,

                        modifier =
                            Modifier.fillMaxSize(),

                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {

                        vm.visibleRouteSegments
                            .forEach { segment ->

                                val isSearchComparison =
                                    vm.search.isNotBlank()

                                val isExpanded =
                                    if (isSearchComparison) {
                                        segment.id in expandedSearchSegmentIds
                                    } else {
                                        expandedSegmentId ==
                                                segment.id
                                    }

                                item(

                                    key =
                                        "segment_${segment.id}"

                                ) {

                                    RouteSegmentCard(

                                        segment =
                                            segment,

                                        expanded =
                                            isExpanded,

                                        onClick = {

                                            if (isSearchComparison) {
                                                expandedSearchSegmentIds =
                                                    if (isExpanded) {
                                                        expandedSearchSegmentIds -
                                                                segment.id
                                                    } else {
                                                        expandedSearchSegmentIds +
                                                                segment.id
                                                    }
                                            } else {
                                                val newExpandedId =
                                                    if (
                                                        isExpanded
                                                    ) {
                                                        null
                                                    } else {
                                                        segment.id
                                                    }

                                                expandedSegmentId =
                                                    newExpandedId

                                                vm.updateExpandedSegment(
                                                    newExpandedId
                                                )
                                            }
                                        }
                                    )
                                }

                                item(

                                    key =
                                        "records_${segment.id}"

                                ) {

                                    AnimatedVisibility(

                                        visible =
                                            isExpanded,

                                        enter =
                                            expandVertically(
                                                animationSpec =
                                                    tween(
                                                        durationMillis = 300,
                                                        easing =
                                                            FastOutSlowInEasing
                                                    )
                                            ) +
                                                    fadeIn(
                                                        animationSpec =
                                                            tween(
                                                                durationMillis = 220
                                                            )
                                                    ),

                                        exit =
                                            shrinkVertically(
                                                animationSpec =
                                                    tween(
                                                        durationMillis = 250
                                                    )
                                            ) +
                                                    fadeOut(
                                                        animationSpec =
                                                            tween(
                                                                durationMillis = 160
                                                            )
                                                    )
                                    ) {

                                        Column(

                                            modifier =
                                                Modifier.fillMaxWidth(),

                                            verticalArrangement =
                                                Arrangement.spacedBy(
                                                    8.dp
                                                )
                                        ) {

                                            segment.records
                                                .forEach { record ->

                                                    val duplicate =
                                                        vm.duplicateMeters
                                                            .contains(
                                                                record.meter
                                                            )

                                                    MeterRecordCard(

                                                        record =
                                                            record,

                                                        duplicate =
                                                            duplicate,

                                                        onClick = {

                                                            selectedRecord =
                                                                record
                                                        },

                                                        onViewMap = {
                                                            mapFocusRowNumber = record.rowNumber
                                                            showMap = true
                                                        }
                                                    )
                                                }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // DIÁLOGO DE LECTURA
        // ---------------------------------------------------------------------

        selectedRecord?.let { record ->

            ReadingDialog(

                record =
                    record,

                vm =
                    vm,

                onDismiss = {
                    selectedRecord = null
                }
            )
        }

        // ---------------------------------------------------------------------
        // MENSAJE
        // ---------------------------------------------------------------------

        vm.message?.let { message ->

            Surface(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),

                shadowElevation =
                    8.dp
            ) {

                Text(

                    text =
                        message,

                    modifier =
                        Modifier.padding(16.dp)
                )
            }
        }
    }
}