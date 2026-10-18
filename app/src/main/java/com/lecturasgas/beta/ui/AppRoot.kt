package com.lecturasgas.beta.ui

import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import com.lecturasgas.beta.ui.icons.AppIcons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.delivery.DeliveryScreen
import com.lecturasgas.beta.delivery.DeliveryViewModel
import com.lecturasgas.beta.map.MapScreen
import com.lecturasgas.beta.readings.ConsumptionCheck
import com.lecturasgas.beta.readings.ReadingDialog
import com.lecturasgas.beta.readings.ReadingsScreen
import com.lecturasgas.beta.review.ExportReview
import com.lecturasgas.beta.ui.MainViewModel.Companion.PAGE_DASHBOARD
import com.lecturasgas.beta.dashboard.DashboardScreen
import com.lecturasgas.beta.ui.MainViewModel.Companion.PAGE_DELIVERY
import com.lecturasgas.beta.ui.MainViewModel.Companion.PAGE_MAP
import com.lecturasgas.beta.ui.MainViewModel.Companion.PAGE_READINGS
import com.lecturasgas.beta.ui.components.Dot
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.TimeUtil
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(main: MainViewModel = viewModel(), deliveryVm: DeliveryViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val route by main.route.collectAsStateWithLifecycle()
    val unexported by main.unexported.collectAsStateWithLifecycle()
    val busy by main.busy.collectAsStateWithLifecycle()
    val report by main.report.collectAsStateWithLifecycle()
    val message by main.message.collectAsStateWithLifecycle()
    val readingRow by main.readingRow.collectAsStateWithLifecycle()
    val records by main.repo.records.collectAsStateWithLifecycle()
    val fix by main.fix.collectAsStateWithLifecycle()
    val settings by main.settings.collectAsStateWithLifecycle()
    val settingsOpen by main.settingsOpen.collectAsStateWithLifecycle()
    val importState by main.importState.collectAsStateWithLifecycle()
    val routesOpen by main.routesOpen.collectAsStateWithLifecycle()
    val reviewOpen by main.reviewOpen.collectAsStateWithLifecycle()
    val orderOpen by main.readingOrderOpen.collectAsStateWithLifecycle()
    val meterHistory by main.meterHistory.collectAsStateWithLifecycle()
    val savedRoutes by main.routes.collectAsStateWithLifecycle()
    val suspects by main.suspects.collectAsStateWithLifecycle()
    val suspectsOpen by main.suspectsOpen.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val typicalConsumption = remember(records) { ConsumptionCheck.typical(records) }
    val gpsEnabled by main.gpsEnabled.collectAsStateWithLifecycle()
    val pageRequest by main.pageRequest.collectAsStateWithLifecycle()
    val history by main.history.collectAsStateWithLifecycle()
    val session by deliveryVm.session.collectAsStateWithLifecycle()

    val pager = rememberPagerState(initialPage = PAGE_DASHBOARD) { 4 }
    var confirmImport by remember { mutableStateOf(false) }
    var startDayAfterPermission by remember { mutableStateOf(false) }

    // ---------------- Archivos ----------------
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) main.importExcel(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        if (uri != null) main.exportExcel(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) main.exportDeliveriesCsv(uri)
    }
    val compareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) main.compareExportedWithOriginal(uri)
    }
    val listLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) main.exportReadingsList(uri)
    }
    val orderCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) main.exportDeliveryOrder(uri, asText = false)
    }
    val orderTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) main.exportDeliveryOrder(uri, asText = true)
    }
    val readingsTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) main.exportReadingsText(uri)
    }
    val orderDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) main.exportReadingOrderText(uri)
    }
    val backupSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) main.saveBackupTo(uri) }
    val backupOpenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) main.inspectBackup(uri)
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) main.setBackupFolder(uri)
    }
    val launchImport: () -> Unit = { importLauncher.launch(arrayOf(XLSX, "application/octet-stream", "*/*")) }
    val requestImport: () -> Unit = {
        if (unexported > 0) {
            confirmImport = true
        } else {
            launchImport()
        }
    }
    // Con la revisión activada, Exportar primero muestra la lista de lo raro; desde ahí se exporta.
    val requestExport: () -> Unit = {
        if (settings.reviewBeforeExport) main.openExportReview() else exportLauncher.launch(main.suggestedExportName())
    }
    val requestCsv: () -> Unit = {
        val name = (route?.name ?: "Ruta").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_')
        csvLauncher.launch("Recorrido_${name}_${TimeUtil.fileStamp(System.currentTimeMillis())}.csv")
    }

    val requestReadingsTxt: () -> Unit = {
        val name = (route?.name ?: "Ruta").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_')
        readingsTxtLauncher.launch("Respaldo_lecturas_${name}_${TimeUtil.fileStamp(System.currentTimeMillis())}.txt")
    }
    val requestCompare: () -> Unit = { compareLauncher.launch(arrayOf(XLSX, "application/octet-stream", "*/*")) }
    val requestReadingsList: () -> Unit = {
        val name = (route?.name ?: "Ruta").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_')
        listLauncher.launch("Lista_lecturas_${name}_${TimeUtil.fileStamp(System.currentTimeMillis())}.csv")
    }
    val requestOrderCsv: () -> Unit = { orderCsvLauncher.launch("Orden_de_entrega_${TimeUtil.fileStamp(System.currentTimeMillis())}.csv") }
    val requestOrderTxt: () -> Unit = { orderTxtLauncher.launch("Orden_de_entrega_${TimeUtil.fileStamp(System.currentTimeMillis())}.txt") }

    // ---------------- Permisos ----------------
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        main.refreshLocation()
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                main.tracker.hasPermission()
        if (startDayAfterPermission) {
            startDayAfterPermission = false
            if (granted) deliveryVm.startDay { main.showMessage(it) }
            else main.showMessage("Sin permiso de ubicación no se puede grabar el recorrido")
        }
    }
    fun permissionsToAsk(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    LaunchedEffect(Unit) {
        if (!main.tracker.hasPermission()) permissionLauncher.launch(permissionsToAsk())
    }

    // Aviso: si solo dio ubicación aproximada, el GPS no puede ser preciso.
    var coarseOnly by remember { mutableStateOf(false) }
    var coarseDismissed by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coarseOnly = main.tracker.hasPermission() && !main.tracker.hasPrecisePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (coarseOnly && !coarseDismissed) {
        AlertDialog(
            onDismissRequest = { coarseDismissed = true },
            title = { Text("Ubicación precisa desactivada") },
            text = {
                Text(
                    "La app tiene permiso de ubicación aproximada: el GPS puede errar por cientos de metros y " +
                            "los puntos de los medidores quedarían mal. Activa «Usar ubicación precisa» en los permisos de la app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    coarseDismissed = true
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Abrir ajustes") }
            },
            dismissButton = { TextButton(onClick = { coarseDismissed = true }) { Text("Ahora no") } }
        )
    }

    val startDay: () -> Unit = {
        if (main.tracker.hasPermission()) {
            if (!gpsEnabled) main.showMessage("Activa el GPS del celular para grabar el recorrido")
            deliveryVm.startDay { main.showMessage(it) }
        } else {
            startDayAfterPermission = true
            permissionLauncher.launch(permissionsToAsk())
        }
    }

    // ---------------- Efectos ----------------
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        main.consumeMessage()
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(m, duration = SnackbarDuration.Short)
    }
    LaunchedEffect(pager.currentPage) { main.currentPage = pager.currentPage }
    LaunchedEffect(pageRequest) {
        val p = pageRequest ?: return@LaunchedEffect
        main.consumePageRequest()
        // En otra corrutina: al consumir el pedido este efecto se reinicia y cancelaría la animación.
        scope.launch { pager.animateScrollToPage(p) }
    }
    // Pantalla encendida mientras la jornada está activa.
    val view = LocalView.current
    DisposableEffect(session != null, settings.keepScreenOn) {
        view.keepScreenOn = session != null || settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Con el teclado abierto: la pantalla se acomoda ARRIBA del teclado y se esconde la barra
    // de abajo (Lecturas/Mapa/Entrega), para que la búsqueda y los resultados tengan espacio.
    val imeVisible = WindowInsets.isImeVisible
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopBar(
                title = route?.name ?: "Lecturas Gas",
                unexported = unexported,
                fix = fix,
                gpsEnabled = gpsEnabled,
                hasRoute = route != null,
                onGpsClick = {
                    if (!gpsEnabled) runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    else if (!main.tracker.hasPermission()) permissionLauncher.launch(permissionsToAsk())
                    else main.refreshLocation()
                },
                onImport = requestImport,
                onExport = requestExport,
                onExportCsv = requestCsv,
                onReadingsList = requestReadingsList,
                onCompare = requestCompare,
                onCheckExport = { main.checkExport() },
                onOrderCsv = requestOrderCsv,
                onReadingsTxt = requestReadingsTxt,
                onOrderTxt = requestOrderTxt,
                onHistory = { main.openHistory() },
                onReadingOrder = { main.openReadingOrder() },
                onBackup = { main.openBackup() },
                onSettings = { main.openSettings() },
                onRoutes = { main.openRoutes() }
            )
        },
        bottomBar = {
            if (!imeVisible) BottomBar(
                current = pager.currentPage,
                onPage = { page -> scope.launch { pager.animateScrollToPage(page) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // Deslizar entre módulos (como Facebook). En el mapa se desactiva
        // para que el dedo mueva el mapa y no cambie de pantalla.
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding),
            userScrollEnabled = pager.currentPage != PAGE_MAP,
            beyondViewportPageCount = if (LocalLite.current) 1 else 2
        ) { page ->
            when (page) {
                PAGE_DASHBOARD -> DashboardScreen(main = main, onImport = requestImport, onExport = requestExport)
                PAGE_READINGS -> ReadingsScreen(main = main, onImport = requestImport)
                PAGE_MAP -> MapScreen(main = main, deliveryVm = deliveryVm)
                else -> DeliveryScreen(main = main, onImport = requestImport, onStartDay = startDay, vm = deliveryVm)
            }
        }
    }

    // ---------------- Diálogos globales ----------------
    importState?.let { st ->
        ImportRouteDialog(
            state = st,
            onKeyChange = { main.selectImportKey(it) },
            onConfirm = { main.confirmImportRoute() },
            onCancel = { main.cancelImport() }
        )
    }
    if (routesOpen) {
        RoutesDialog(
            routes = savedRoutes,
            onSwitch = { main.switchRoute(it) },
            onImport = { main.closeRoutes(); requestImport() },
            onDelete = { id, keep -> main.deleteRoute(id, keep) },
            onClose = { main.closeRoutes() }
        )
    }
    if (suspectsOpen) {
        SuspectsDialog(
            suspects = suspects,
            fix = fix,
            onShowOnMap = { row -> main.closeSuspects(); main.showOnMap(row) },
            onClear = { row -> main.clearSuspect(row) },
            onClearAll = { main.clearAllSuspects() },
            onClose = { main.closeSuspects() }
        )
    }
    if (settingsOpen) {
        SettingsDialog(
            settings = settings,
            onChange = main::updateSettings,
            onOpenBackup = { main.closeSettings(); main.openBackup() },
            suspectCount = suspects.size,
            onReviewLocations = { main.closeSettings(); main.openSuspects() },
            onClose = { main.closeSettings() }
        )
    }
    // Un punto puesto a mano que las visitas contradicen: se pregunta antes de cambiarlo.
    val proposal by main.repo.locationProposal.collectAsStateWithLifecycle()
    proposal?.let { pr ->
        AlertDialog(
            onDismissRequest = { main.repo.dismissProposal() },
            title = { Text("¿Mover este medidor?") },
            text = {
                Text(
                    "NIR ${pr.nir} · ${pr.user}\n\n" +
                        "El punto que marcaste a mano está a unos ${com.lecturasgas.beta.util.Geo.formatDistance(pr.meters)} " +
                        "de donde estás ahora (GPS ±${pr.accuracy.toInt()} m), y las visitas anteriores también lo contradicen.\n\n" +
                        "Si mueves el punto aquí, el mapa y «Estás frente a…» usarán esta ubicación."
                )
            },
            confirmButton = { TextButton(onClick = { main.repo.acceptProposal() }) { Text("Mover aquí", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { main.repo.dismissProposal() }) { Text("Dejarlo como está") } }
        )
    }
    if (reviewOpen) {
        // Se recalcula sola cuando corriges una lectura desde la lista.
        val review = remember(records) { ExportReview.analyze(records) }
        ExportReviewDialog(
            review = review,
            routeName = route?.name ?: "Ruta",
            onOpenMeter = { row -> main.openReading(row) },
            onCheck = { main.checkExport() },
            onExport = { main.closeExportReview(); exportLauncher.launch(main.suggestedExportName()) },
            onClose = { main.closeExportReview() }
        )
    }
    if (orderOpen) {
        ReadingOrderDialog(
            records = records,
            routeName = route?.name ?: "Ruta",
            onOpenMeter = { row -> main.openReading(row) },
            onSave = {
                val name = (route?.name ?: "Ruta").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_')
                orderDocLauncher.launch("Orden_de_lectura_${name}_${TimeUtil.fileStamp(System.currentTimeMillis())}.txt")
            },
            onClose = { main.closeReadingOrder() }
        )
    }
    readingRow?.let { row ->
        val record = records.firstOrNull { it.row == row }
        if (record == null) {
            LaunchedEffect(row) { main.closeReading() }
        } else {
            val gpsQuality by main.gpsQuality.collectAsStateWithLifecycle()
            ReadingDialog(
                record = record,
                fix = fix,
                quality = gpsQuality,
                calibrate = settings.calibrateGps,
                initialSaveGps = main.prefs.saveGpsWithReading,
                initialAfter = main.prefs.afterSave,
                onDismiss = { main.closeReading() },
                onSave = { value, note, saveGps, after ->
                    if (settings.vibrateSave) runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    main.saveReading(row, value, note, saveGps, after)
                },
                onClear = { main.clearReading(row) },
                onSetMeterFix = { real -> main.setMeterFix(row, real) },
                meterFixWarning = { text -> main.meterFixWarning(row, text) },
                typicalConsumption = typicalConsumption,
                consumptionAlert = settings.consumptionAlert,
                onSkip = { reason, note, after -> main.saveSkip(row, reason, note, after) },
                onClearSkip = { main.clearSkip(row) },
                onShowMap = { main.closeReading(); main.showOnMap(row) },
                onAdjustLocation = { main.closeReading(); main.adjustOnMap(row) },
                onHistory = { main.openMeterHistory(row) }
            )
        }
    }
    meterHistory?.let { (row, view) ->
        records.firstOrNull { it.row == row }?.let { rec -> MeterHistoryDialog(rec, view) { main.closeMeterHistory() } }
    }

    busy?.let { b ->
        if (b.fraction >= 0f) {
            TaskProgressDialog(b, onCancel = { main.cancelBusy() })
            return@let
        }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(b.title) },
            text = {
                Column {
                    if (b.total > 0) {
                        val f = (b.done.toFloat() / b.total).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("${(f * 100).roundToInt()}%  ·  ${b.done} / ${b.total} filas")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Un momento…")
                    }
                }
            }
        )
    }

    report?.let { r ->
        AlertDialog(
            onDismissRequest = { main.closeReport() },
            title = { Text(r.title) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    r.lines.forEach { line ->
                        when {
                            line.isEmpty() -> Spacer(Modifier.height(6.dp))
                            // Las líneas que ya traen su propia viñeta o sangría se muestran tal cual.
                            line.startsWith("•") || line.startsWith(" ") || line.endsWith(":") ->
                                Text(line, modifier = Modifier.padding(vertical = 2.dp),
                                    fontWeight = if (line.endsWith(":")) FontWeight.Bold else FontWeight.Normal)
                            else -> Text("• $line", modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { main.closeReport() }) { Text("Entendido", fontWeight = FontWeight.Bold) } }
        )
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Tienes $unexported lecturas sin exportar") },
            text = {
                Text(
                    "Si el Excel es de OTRA ruta, esta queda guardada y no pierdes nada. " +
                            "Si es la MISMA ruta de otro mes, esas lecturas no pasan al nuevo archivo: " +
                            "exporta primero. (Si es el mismo mes, se conservan.)"
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmImport = false; requestExport() }) { Text("Exportar primero", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmImport = false }) { Text("Cancelar") }
                    TextButton(onClick = { confirmImport = false; launchImport() }) { Text("Importar igual", color = AppColors.Error) }
                }
            }
        )
    }

    BackupDialogs(
        main = main,
        onSaveFile = { backupSaveLauncher.launch(main.suggestedBackupName()) },
        onShare = {
            main.shareBackup { intent ->
                runCatching { context.startActivity(Intent.createChooser(intent, "Enviar respaldo")) }
            }
        },
        onRestorePick = { backupOpenLauncher.launch(arrayOf("*/*")) },
        onPickFolder = { backupFolderLauncher.launch(null) }
    )

    history?.let { list ->
        HistoryDialog(list, main) { main.closeHistory() }
    }
}

@Composable
private fun BottomBar(current: Int, onPage: (Int) -> Unit) {
    Surface(color = AppColors.Card, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem(AppIcons.Home, "Inicio", current == PAGE_DASHBOARD, Modifier.weight(1f)) { onPage(PAGE_DASHBOARD) }
            BarItem(AppIcons.Pencil, "Lecturas", current == PAGE_READINGS, Modifier.weight(1f)) { onPage(PAGE_READINGS) }
            BarItem(AppIcons.Pin, "Mapa", current == PAGE_MAP, Modifier.weight(1f)) { onPage(PAGE_MAP) }
            BarItem(AppIcons.CheckCircle, "Entrega", current == PAGE_DELIVERY, Modifier.weight(1f)) { onPage(PAGE_DELIVERY) }
        }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else AppColors.Muted
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, color = tint)
        Box(
            Modifier.padding(top = 3.dp).size(width = 18.dp, height = 3.dp)
                .background(if (selected) tint else Color.Transparent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    unexported: Int,
    fix: GeoFix?,
    gpsEnabled: Boolean,
    hasRoute: Boolean,
    onGpsClick: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onExportCsv: () -> Unit,
    onReadingsList: () -> Unit,
    onCompare: () -> Unit,
    onCheckExport: () -> Unit,
    onOrderCsv: () -> Unit,
    onReadingsTxt: () -> Unit,
    onOrderTxt: () -> Unit,
    onHistory: () -> Unit,
    onReadingOrder: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onRoutes: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = AppColors.TopBar, contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(AppIcons.Menu, contentDescription = "Menú", tint = Color.White) }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.background(AppColors.Card).widthIn(min = 280.dp)
                ) {
                    MenuHeader("RUTA")
                    MenuRow(AppIcons.Folder, "Mis rutas (A, B, C…)") { menu = false; onRoutes() }
                    MenuRow(AppIcons.Import, "Importar Excel de la ruta") { menu = false; onImport() }
                    if (hasRoute) {
                        MenuRow(AppIcons.Eye, "Orden de lectura (1.ª, 2.ª, 3.ª…)") { menu = false; onReadingOrder() }
                        MenuHeader("EXPORTAR")
                        MenuRow(AppIcons.Export, "Exportar Excel con lecturas") { menu = false; onExport() }
                        MenuRow(AppIcons.Check, "Comprobar exportación (sin guardar)") { menu = false; onCheckExport() }
                        MenuRow(AppIcons.Compare, "Comparar un Excel con el original") { menu = false; onCompare() }
                        MenuRow(AppIcons.ListLines, "Lista de lecturas (respaldo)") { menu = false; onReadingsList() }
                        MenuRow(AppIcons.Share, "Respaldo de lecturas (texto)") { menu = false; onReadingsTxt() }
                    }
                    MenuHeader("ENTREGA")
                    if (hasRoute) MenuRow(AppIcons.Export, "Recorrido de entrega (CSV)") { menu = false; onExportCsv() }
                    MenuRow(AppIcons.Box, "Orden de entrega completo (Excel)") { menu = false; onOrderCsv() }
                    MenuRow(AppIcons.ListLines, "Orden de entrega completo (texto)") { menu = false; onOrderTxt() }
                    MenuRow(AppIcons.Clock, "Historial de jornadas") { menu = false; onHistory() }
                    MenuHeader("RESPALDO")
                    MenuRow(AppIcons.Database, "Respaldo y traspaso a otro celular") { menu = false; onBackup() }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onGpsClick)
                ) {
                    val acc = fix?.accuracy
                    val fresh = fix != null && System.currentTimeMillis() - fix.time < 60_000L
                    val (color, text) = when {
                        !gpsEnabled -> Color(0xFFFF6B6B) to "GPS apagado (toca para activar)"
                        fix == null || !fresh -> Color(0xFFFFC107) to "Buscando GPS…"
                        acc != null && acc <= 15f -> Color(0xFF69F0AE) to "GPS ±${acc.roundToInt()} m"
                        acc != null -> Color(0xFFFFC107) to "GPS ±${acc.roundToInt()} m"
                        else -> Color(0xFF69F0AE) to "GPS activo"
                    }
                    Dot(color, 8.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(text, fontSize = 13.sp, color = Color(0xFFD6E3F2))
                    if (unexported > 0) {
                        Text("  · $unexported sin exportar", fontSize = 13.sp, color = Color(0xFFFFE0B2), fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onSettings) { Icon(AppIcons.Gear, contentDescription = "Ajustes", tint = Color.White) }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Black, color = AppColors.Muted,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 15.sp) },
        leadingIcon = {
            Box(
                Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        },
        onClick = onClick
    )
}
