package com.lecturasgas.beta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.ui.MeterRecordCard
import com.lecturasgas.beta.ui.ReadingDialog
import com.lecturasgas.beta.ui.RouteSegmentCard
import com.lecturasgas.beta.viewmodel.ReadingViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        setContent {
            App()
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun App(
    vm: ReadingViewModel =
        viewModel()
) {

    var selected by
    remember {
        mutableStateOf<MeterRecord?>(null)
    }

    val expandedSegments =
        remember {
            mutableStateMapOf<Int, Boolean>()
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                vm.importExcel(uri)
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

    LaunchedEffect(
        vm.message
    ) {

        if (vm.message != null) {

            kotlinx.coroutines.delay(
                2500
            )

            vm.clearMessage()
        }
    }

    MaterialTheme {

        Scaffold(

            topBar = {

                TopAppBar(
                    title = {
                        Text(
                            "Lecturas Gas Beta"
                        )
                    }
                )
            }

        ) { paddingValues ->

            Column(

                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            paddingValues
                        )
                        .padding(12.dp)

            ) {

                // -------------------------------------------------------------
                // BOTONES
                // -------------------------------------------------------------

                Button(

                    onClick = {

                        importLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/octet-stream"
                            )
                        )
                    },

                    enabled =
                        !vm.isBusy,

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text(
                        "Importar Excel"
                    )
                }

                Spacer(
                    Modifier.height(8.dp)
                )

                OutlinedButton(

                    onClick = {

                        exportLauncher.launch(
                            "Lecturas_A_terminado.xlsx"
                        )
                    },

                    enabled =
                        vm.imported &&
                                !vm.isBusy,

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text(
                        "Exportar"
                    )
                }

                Spacer(
                    Modifier.height(8.dp)
                )

                // -------------------------------------------------------------
                // RESUMEN
                // -------------------------------------------------------------

                if (vm.imported) {

                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Column(
                            Modifier.padding(12.dp)
                        ) {

                            Text(
                                "Ruta A",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            Text(
                                "${vm.records.size} registros  •  " +
                                        "✓ ${vm.readCount} leídos  •  " +
                                        "🔴 ${vm.pendingCount} pendientes"
                            )

                            Text(
                                "Tramos: ${vm.routeSegments.size}",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                } else {

                    Text(
                        "Importa el Excel de la ruta para comenzar."
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )
                }

                // -------------------------------------------------------------
                // BUSCADOR
                // -------------------------------------------------------------

                OutlinedTextField(

                    value =
                        vm.search,

                    onValueChange = {
                        vm.search = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    label = {

                        Text(
                            "Buscar medidor, últimos 4 dígitos, dirección o usuario"
                        )
                    },

                    singleLine = true
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                if (vm.imported) {

                    Text(

                        if (
                            vm.search.isBlank()
                        ) {
                            "Tramos de la ruta"
                        } else {
                            "Resultados"
                        },

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        Modifier.height(4.dp)
                    )
                }

                // -------------------------------------------------------------
                // LISTA DE TRAMOS
                // -------------------------------------------------------------

                LazyColumn(

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        ),

                    modifier =
                        Modifier.fillMaxSize()

                ) {

                    vm.visibleRouteSegments
                        .forEach { segment ->

                            val segmentKey =
                                segment.id

                            val expanded =
                                expandedSegments[
                                    segmentKey
                                ] ?: false

                            item(
                                key =
                                    "segment:$segmentKey"
                            ) {

                                RouteSegmentCard(

                                    segment = segment,

                                    expanded = expanded,

                                    onClick = {

                                        expandedSegments[
                                            segmentKey
                                        ] =
                                            !expanded
                                    }
                                )
                            }

                            if (expanded) {

                                segment.records
                                    .forEach { record ->

                                        item(
                                            key =
                                                "record:${record.rowNumber}"
                                        ) {

                                            val duplicate =
                                                vm.duplicateMeters
                                                    .contains(
                                                        record.meter
                                                    )

                                            MeterRecordCard(

                                                record = record,

                                                duplicate =
                                                    duplicate,

                                                onClick = {

                                                    selected =
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

        // ---------------------------------------------------------------------
        // DIÁLOGO DE LECTURA
        // ---------------------------------------------------------------------

        selected?.let { record ->

            ReadingDialog(

                record = record,

                vm = vm,

                onDismiss = {
                    selected = null
                }
            )
        }

        // ---------------------------------------------------------------------
        // MENSAJE
        // ---------------------------------------------------------------------

        vm.message?.let { msg ->

            Surface(

                modifier =
                    Modifier.padding(
                        16.dp
                    ),

                shadowElevation =
                    8.dp

            ) {

                Text(
                    msg,
                    Modifier.padding(
                        16.dp
                    )
                )
            }
        }
    }
}

