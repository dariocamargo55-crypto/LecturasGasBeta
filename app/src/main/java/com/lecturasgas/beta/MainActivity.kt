package com.lecturasgas.beta

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    val listState = rememberLazyListState()

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                vm.importExcel(uri)
                expandedSegmentId = null
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

    LaunchedEffect(vm.message) {

        if (vm.message != null) {

            kotlinx.coroutines.delay(2500)

            vm.clearMessage()
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
                            "Lecturas Gas"
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

                                text = {
                                    Text(
                                        "Importar Excel"
                                    )
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

                                text = {
                                    Text(
                                        "Exportar Excel"
                                    )
                                },

                                onClick = {

                                    menuExpanded = false

                                    exportLauncher.launch(
                                        "Lecturas_A_terminado.xlsx"
                                    )
                                },

                                enabled =
                                    vm.imported &&
                                            !vm.isBusy
                            )
                        }
                    }
                )
            }

        ) { paddingValues ->

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

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Card(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec =
                                        tween(
                                            durationMillis = 350
                                        )
                                ),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                            ),

                        elevation =
                            CardDefaults.cardElevation(
                                defaultElevation = 3.dp
                            )
                    ) {

                        Column(

                            modifier =
                                Modifier.padding(
                                    horizontal = 20.dp,
                                    vertical = 20.dp
                                ),

                            verticalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {

                            Text(

                                text =
                                    vm.routeName
                                        ?: "Ruta sin nombre",

                                style =
                                    MaterialTheme
                                        .typography
                                        .headlineLarge,

                                fontWeight =
                                    FontWeight.Bold,

                                letterSpacing =
                                    0.2.sp
                            )

                            Text(

                                text =
                                    "${vm.records.size} medidores",

                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(2.dp)
                            )

                            Row(

                                modifier =
                                    Modifier.fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.SpaceBetween,

                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Text(

                                    text =
                                        "✓ ${vm.readCount} leídos",

                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyLarge,

                                    fontWeight =
                                        FontWeight.Medium,

                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                )

                                Text(

                                    text =
                                        "${vm.pendingCount} pendientes",

                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyLarge,

                                    fontWeight =
                                        FontWeight.Medium,

                                    color =
                                        if (
                                            vm.pendingCount > 0
                                        ) {
                                            MaterialTheme
                                                .colorScheme
                                                .error
                                        } else {
                                            MaterialTheme
                                                .colorScheme
                                                .primary
                                        }
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(2.dp)
                            )

                            LinearProgressIndicator(

                                progress =
                                    animatedProgress,

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(2.dp)
                            )

                            Text(

                                text =
                                    "$uniqueNeighborhoods barrios",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                } else {

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )

                    Text(

                        text =
                            "Importa el Excel de la ruta para comenzar.",

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )
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

                            val isExpanded =
                                expandedSegmentId ==
                                        segment.id

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
