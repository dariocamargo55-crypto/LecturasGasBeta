package com.lecturasgas.beta.delivery

import androidx.compose.foundation.BorderStroke
import com.lecturasgas.beta.ui.components.ProgressRing
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.lecturasgas.beta.ui.icons.AppIcons
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.ui.MainViewModel
import com.lecturasgas.beta.ui.components.BigButton
import com.lecturasgas.beta.ui.components.EmptyState
import com.lecturasgas.beta.ui.components.RouteTopCard
import com.lecturasgas.beta.ui.components.MeterCard
import com.lecturasgas.beta.ui.components.SearchPanel
import com.lecturasgas.beta.ui.components.Segmented
import com.lecturasgas.beta.ui.components.StatusPill
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.Geo
import com.lecturasgas.beta.util.TimeUtil
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeliveryScreen(
    main: MainViewModel,
    onImport: () -> Unit,
    onStartDay: () -> Unit,
    vm: DeliveryViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val field by vm.field.collectAsStateWithLifecycle()
    val numeric by vm.numeric.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val fix by main.fix.collectAsStateWithLifecycle()
    val loaded by main.loaded.collectAsStateWithLifecycle()
    val route by main.route.collectAsStateWithLifecycle()
    val settings by main.settings.collectAsStateWithLifecycle()

    var confirmEnd by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf<Delivery?>(null) }
    var showDash by remember { mutableStateOf(false) }
    var showPackage by remember { mutableStateOf(false) }
    val allRecords by main.repo.records.collectAsStateWithLifecycle()
    val allDeliveries by main.repo.deliveries.collectAsStateWithLifecycle()
    var noteForRow by remember { mutableStateOf<Int?>(null) }
    var editNoteOf by remember { mutableStateOf<Delivery?>(null) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    if (!ui.hasRoute) {
        if (loaded) {
            EmptyState(
                "No hay una ruta cargada",
                "Importa el Excel de la ruta para empezar a entregar facturas.",
                "Importar Excel", onImport
            )
        }
        return
    }

    val performDeliver: (Int, String) -> Unit = { row, note ->
        if (settings.vibrateDeliver) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.deliver(row, note) { main.showMessage(it) }
    }
    // Con "Preguntar dónde dejé la factura" activo, primero se abre la nota.
    val deliver: (Int) -> Unit = { row ->
        if (settings.askNoteOnDeliver) noteForRow = row else performDeliver(row, "")
    }

    // Al abrir el teclado, la lista sube hasta dejar el buscador arriba (con sus resultados).
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.isImeVisible
    var nearMe by remember { mutableStateOf(false) }
    val fixKeyLat = fix?.let { (it.lat * 2000).toInt() }
    val fixKeyLon = fix?.let { (it.lon * 2000).toInt() }
    val pendingShown = remember(ui.pending, nearMe, fixKeyLat, fixKeyLon) {
        val f = fix
        if (!nearMe || f == null) ui.pending
        else ui.pending.sortedBy { r ->
            val la = r.lat; val lo = r.lon
            if (la == null || lo == null) Double.MAX_VALUE else Geo.distanceMeters(f.lat, f.lon, la, lo)
        }
    }
    val showNextItem = settings.showNextCard && ui.next != null
    val searchIndex = 1 + (if (ui.lastDelivery != null) 1 else 0) +
            (if (ui.total > 0 && ui.delivered == ui.total) 1 else 0) +
            (if (showNextItem) 1 else 0) + (if (ui.nearby != null) 1 else 0)
    LaunchedEffect(imeVisible) {
        if (imeVisible) listState.animateScrollToItem(searchIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "top") {
            DeliveryTopCard(
                ui = ui,
                onStart = onStartDay,
                onEnd = { confirmEnd = true },
                onPackage = { showPackage = true },
                onOpenSummary = { showDash = true },
                showPackage = !imeVisible && ui.total > ui.delivered
            )
        }
        ui.lastDelivery?.let { last ->
            item(key = "last") {
                LastDeliveryBar(
                    d = last,
                    suggestions = ui.noteSuggestions,
                    onUndo = { vm.undo(last) { main.showMessage(it) } },
                    onNote = { text -> vm.setNote(last, if (text.equals(last.note, ignoreCase = true)) "" else text) { main.showMessage(it) } },
                    onCustom = { editNoteOf = last }
                )
            }
        }
        if (ui.total > 0 && ui.delivered == ui.total) {
            item(key = "finished") { FinishedCard(ui.session != null) }
        }
        ui.next?.takeIf { settings.showNextCard }?.let { next ->
            item(key = "next") {
                if (settings.nextCompact) {
                    NextLine(
                        record = next,
                        distance = ui.nextDistance,
                        bearing = ui.nextBearing,
                        onMap = { main.showOnMap(next.row) },
                        onDeliver = { deliver(next.row) }
                    )
                } else {
                    NextCard(
                        record = next,
                        distance = ui.nextDistance,
                        bearing = ui.nextBearing,
                        learned = ui.learned,
                        previousNote = ui.notes.byMeter[next.meterKey],
                        after = ui.after,
                        afterDistance = ui.afterDistance,
                        onMap = { main.showOnMap(next.row) },
                        onDeliver = { deliver(next.row) },
                        onSkip = { vm.skipNext(next.row) { main.showMessage(it) } }
                    )
                }
            }
        }
        ui.nearby?.let { near ->
            item(key = "nearby") {
                NearbyBanner(near, onDeliver = { deliver(near.record.row) }, onMap = { main.showOnMap(near.record.row) })
            }
        }
        // Buscador y pestañas: se quedan fijos arriba al deslizar la lista.
        stickyHeader(key = "search") {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                SearchPanel(
                    query = query,
                    onQueryChange = vm::setQuery,
                    fields = field,
                    onFieldToggle = vm::toggleField,
                    numeric = numeric,
                    onNumericChange = vm::setNumeric,
                    resultText = if (ui.searching) {
                        "${if (tab == DeliveryTab.PENDING) ui.pending.size else ui.done.size} resultado(s)"
                    } else null
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                    Segmented(
                        options = DeliveryTab.entries.map {
                            it to (if (it == DeliveryTab.PENDING) "${it.label} (${ui.total - ui.delivered})" else "${it.label} (${ui.delivered})")
                        },
                        selected = tab,
                        onSelect = vm::setTab,
                        modifier = Modifier.weight(1f)
                    )
                    if (tab == DeliveryTab.PENDING) {
                        // «Cerca de mí»: ordena por distancia. Naranja mientras busca la señal GPS.
                        val searching = fix == null && nearMe
                        androidx.compose.material3.Surface(
                            onClick = { nearMe = !nearMe },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (nearMe) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = if (nearMe) null else androidx.compose.foundation.BorderStroke(1.5.dp, AppColors.Muted),
                            modifier = Modifier.size(44.dp).semantics { contentDescription = "Cerca de mí" }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    AppIcons.Locate, contentDescription = null,
                                    tint = when {
                                        searching -> AppColors.Warning
                                        nearMe -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (tab == DeliveryTab.PENDING) {
            items(pendingShown, key = { "p_${it.row}" }, contentType = { 1 }) { r ->
                MeterCard(
                    record = r,
                    onClick = { main.showOnMap(r.row) },
                    highlighted = r.row == ui.next?.row,
                    // En la pestaña «Por entregar» no hace falta repetirlo en cada tarjeta.
                    statusText = when {
                        r.row == ui.next?.row -> "Siguiente"
                        r.row in ui.skippedRows -> "Para después"
                        else -> ""
                    },
                    statusColor = when {
                        r.row == ui.next?.row -> AppColors.Next
                        r.row in ui.skippedRows -> AppColors.Skipped
                        else -> AppColors.Pending
                    },
                    showReading = false,
                    fresh = true,
                    extraLine = listOfNotNull(
                        distanceText(fix, r),
                        ui.notes.byMeter[r.meterKey]?.let { "Dejada: $it" }
                    ).joinToString("  ·  ").ifBlank { null },
                    actions = {
                        // Botón redondo ✓ (el mismo de la tarjeta de lectura), con su nombre debajo.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.Surface(
                                onClick = { deliver(r.row) },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = AppColors.Delivered,
                                contentColor = Color.White,
                                modifier = Modifier.size(52.dp).semantics { contentDescription = "Entregar" }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(30.dp))
                                }
                            }
                            Text("Entregar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Muted)
                        }
                    }
                )
            }
            if (ui.pending.isEmpty()) {
                item { EmptyState(if (ui.searching) "Sin resultados" else "¡Todo entregado!", if (ui.searching) "Prueba otro campo de búsqueda." else "No quedan facturas por entregar.") }
            }
        } else {
            items(ui.done, key = { "d_${it.id}" }, contentType = { 2 }) { d ->
                DeliveredRow(d, onUndo = { confirmUndo = d }, onMap = { main.showOnMap(d.row) }, onNote = { editNoteOf = d })
            }
            if (ui.done.isEmpty()) {
                item { EmptyState("Aún no hay entregas", "Las entregas aparecen aquí en el orden real en que las hiciste.") }
            }
        }
    }

    if (showPackage) {
        val doneRows = allDeliveries.map { it.row }.toHashSet()
        PackageDialog(
            pending = allRecords.filter { it.row !in doneRows },
            suggestions = ui.noteSuggestions,
            onConfirm = { rows, note ->
                showPackage = false
                if (settings.vibrateDeliver) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.deliverBatch(rows, note) { main.showMessage(it) }
            },
            onDismiss = { showPackage = false }
        )
    }

    val dashStats = ui.stats
    if (showDash && dashStats != null) {
        val now = System.currentTimeMillis()
        val routeName = route?.name ?: "Ruta"
        val session = ui.session
        DeliveryDashboardDialog(
            routeName = routeName,
            stats = dashStats,
            sessionDistance = session?.distanceMeters,
            sessionDuration = session?.let { TimeUtil.duration(now - it.startedAt) },
            onShowPending = { vm.setQuery(""); vm.setTab(DeliveryTab.PENDING); showDash = false },
            onShowDone = { vm.setQuery(""); vm.setTab(DeliveryTab.DONE); showDash = false },
            onIncludeSkipped = { vm.includeSkipped(); showDash = false },
            onShowBarrio = { name -> vm.showBarrio(name); showDash = false },
            onShare = {
                val date = java.text.SimpleDateFormat("d MMM", java.util.Locale("es", "CO")).format(java.util.Date(now))
                val text = DeliveryDashboard.summaryText(
                    routeName, dashStats, date,
                    session?.let { Geo.formatDistance(it.distanceMeters) },
                    session?.let { TimeUtil.duration(now - it.startedAt) }
                )
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                runCatching { context.startActivity(android.content.Intent.createChooser(send, "Compartir resumen")) }
            },
            onClose = { showDash = false }
        )
    }

    noteForRow?.let { row ->
        val rec = ui.next?.takeIf { it.row == row } ?: ui.pending.firstOrNull { it.row == row } ?: ui.nearby?.record?.takeIf { it.row == row }
        NoteDialog(
            title = "Entregar · NIR ${rec?.nir ?: ""}${rec?.user?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
            previousNote = rec?.let { ui.notes.byMeter[it.meterKey] },
            suggestions = ui.noteSuggestions,
            initial = "",
            confirmText = "ENTREGAR",
            onConfirm = { note -> noteForRow = null; performDeliver(row, note) },
            onSkip = { noteForRow = null; performDeliver(row, "") },
            onDismiss = { noteForRow = null }
        )
    }
    editNoteOf?.let { d ->
        NoteDialog(
            title = "NIR ${d.nir} · ${d.user}",
            previousNote = null,
            suggestions = ui.noteSuggestions,
            initial = d.note,
            confirmText = "GUARDAR",
            onConfirm = { note -> editNoteOf = null; vm.setNote(d, note) { main.showMessage(it) } },
            onSkip = if (d.note.isNotBlank()) ({ editNoteOf = null; vm.setNote(d, "") { main.showMessage(it) } }) else null,
            onDismiss = { editNoteOf = null }
        )
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("¿Terminar la jornada?") },
            text = { Text("Se guarda el recorrido y el orden de entrega. Las facturas que falten siguen pendientes para mañana.") },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; vm.endDay() }) { Text("Terminar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Seguir") } }
        )
    }
    confirmUndo?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmUndo = null },
            title = { Text("¿Deshacer la entrega?") },
            text = {
                val inBatch = if (d.bulk != 0L) allDeliveries.count { it.bulk == d.bulk } else 0
                Text("NIR ${d.nir} · ${d.user}\nVolverá a la lista de pendientes." +
                    if (inBatch > 1) "\n\nEsta factura es parte de un paquete de $inBatch." else "")
            },
            confirmButton = {
                Row {
                    if (d.bulk != 0L) TextButton(onClick = { confirmUndo = null; vm.undoBatch(d.bulk) { main.showMessage(it) } }) {
                        Text("Todo el paquete", color = AppColors.Error, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { confirmUndo = null; vm.undo(d) { main.showMessage(it) } }) {
                        Text(if (d.bulk != 0L) "Solo esta" else "Deshacer", color = AppColors.Error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = null }) { Text("Cancelar") } }
        )
    }
}

private fun distanceText(fix: GeoFix?, r: MeterRecord): String? {
    if (fix == null) return null
    // Sin ubicación: la tarjeta ya lo dice con su etiqueta, no se repite aquí.
    val lat = r.lat ?: return null
    val lon = r.lon ?: return null
    val d = Geo.distanceMeters(fix.lat, fix.lon, lat, lon)
    return "${Geo.arrowFor(Geo.bearingDegrees(fix.lat, fix.lon, lat, lon))} ${Geo.formatDistance(d)}"
}

/** Tarjeta única de arriba (la misma de Lecturas): anillo grande + botones circulares. */
@Composable
private fun DeliveryTopCard(
    ui: DeliveryUi,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onPackage: () -> Unit,
    onOpenSummary: () -> Unit,
    showPackage: Boolean
) {
    val stats = ui.stats ?: return
    RouteTopCard(
        fraction = stats.fraction, percent = stats.percent, color = AppColors.Delivered,
        done = stats.delivered, total = stats.total, pending = stats.pending,
        extraPill = if (stats.skipped > 0) "${stats.skipped} después" else null,
        extraColor = AppColors.Skipped,
        detail = "Hoy ${stats.deliveredToday}" + (stats.perHour?.let { " · ${it.toInt()}/h" } ?: ""),
        session = ui.session,
        onStart = onStart, onEnd = onEnd, onOpenSummary = onOpenSummary,
        onPackage = if (showPackage) onPackage else null
    )
}

@Composable
private fun SessionCard(ui: DeliveryUi, onStart: () -> Unit, onEnd: () -> Unit) {
    val session = ui.session
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = if (session != null) AppColors.TintGreen else AppColors.Card)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (session == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Jornada de entrega", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Graba tu recorrido y el orden de entrega", color = AppColors.Muted, fontSize = 13.sp)
                    }
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Done),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("INICIAR", fontWeight = FontWeight.Black) }
                }
            } else {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(session.id) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(30_000)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill("● GRABANDO RECORRIDO", AppColors.Done)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onEnd) { Text("Terminar") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Hoy", "${ui.deliveredToday}", Modifier.weight(1f))
                    Stat("Distancia", Geo.formatDistance(session.distanceMeters), Modifier.weight(1f))
                    Stat("Tiempo", TimeUtil.duration(now - session.startedAt), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = AppColors.Muted)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun NextCard(
    record: MeterRecord,
    distance: Double?,
    bearing: Double?,
    learned: Boolean,
    previousNote: String?,
    after: MeterRecord?,
    afterDistance: Double?,
    onMap: () -> Unit,
    onDeliver: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, AppColors.Next),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.TintOrange)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Línea 1: SIGUIENTE · NIR ............ flecha + distancia
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SIGUIENTE", fontWeight = FontWeight.Black, color = AppColors.Next, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text("NIR ${record.nir}", fontSize = 19.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                if (distance != null && bearing != null) {
                    Text(
                        "${Geo.arrowFor(bearing)} ${Geo.formatDistance(distance)}",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                } else if (!record.hasLocation) {
                    Text("Sin GPS", color = AppColors.Muted, fontSize = 13.sp)
                }
            }
            // Línea 2: usuario · dirección (todo en una)
            Text(
                record.user,
                fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                record.address + " · " + record.displayMeter,
                color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (previousNote != null) {
                Text(
                    "La vez pasada: $previousNote",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.Warning,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onMap,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(AppIcons.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mapa", fontSize = 15.sp)
                }
                Button(
                    onClick = onDeliver,
                    modifier = Modifier.weight(1.6f).heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Delivered),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("ENTREGAR", fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (after != null) "Luego: NIR ${after.nir}" + (afterDistance?.let { " · ${Geo.formatDistance(it)}" } ?: "")
                    else if (learned) "según tu ruta aprendida" else "según orden NIR"),
                    fontSize = 12.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Visitar después", fontSize = 13.sp)
                }
            }
        }
    }
}

/** Sugerencia mínima del siguiente: una sola fila (NIR, distancia, nombre, Mapa y Entregar). */
@Composable
private fun NextLine(
    record: MeterRecord,
    distance: Double?,
    bearing: Double?,
    onMap: () -> Unit,
    onDeliver: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.Next),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.TintOrange)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "SIGUIENTE · NIR ${record.nir}" +
                        (if (distance != null && bearing != null) " · ${Geo.arrowFor(bearing)} ${Geo.formatDistance(distance)}"
                        else if (!record.hasLocation) " · sin GPS" else ""),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(record.user, fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onMap, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Mapa") }
            Button(
                onClick = onDeliver,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Delivered),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Entregar", fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Aparece justo después de entregar: permite deshacer y, con un toque, anotar dónde se dejó
 * la factura (primera puerta, puerta blanca…).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastDeliveryBar(
    d: Delivery,
    suggestions: List<String>,
    onUndo: () -> Unit,
    onNote: (String) -> Unit,
    onCustom: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.CheckCircle, contentDescription = null, tint = AppColors.Delivered, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Última entrega", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Muted)
                    Text(
                        "#${d.order} · NIR ${d.nir} · ${TimeUtil.hour(d.deliveredAt)}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onUndo, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                    Text("Deshacer", color = AppColors.Error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                if (d.note.isBlank()) "¿Dónde se deja la factura?" else "Dejada: ${d.note}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (d.note.isBlank()) MaterialTheme.colorScheme.onSurface else AppColors.Warning,
                modifier = Modifier.padding(top = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 14.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                items(suggestions, key = { it }) { text ->
                    FilterChip(
                        selected = d.note.equals(text, ignoreCase = true),
                        onClick = { onNote(text) },
                        label = { Text(text, fontSize = 14.sp) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                item(key = "custom") {
                    AssistChip(onClick = onCustom, shape = RoundedCornerShape(50), label = { Text("✏ Otra…", fontSize = 14.sp) })
                }
            }
        }
    }
}

/** Escribir o elegir dónde se deja (o se dejó) la factura. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteDialog(
    title: String,
    previousNote: String?,
    suggestions: List<String>,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onSkip: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("¿Dónde se deja o se dejó la factura?", fontSize = 13.sp, color = AppColors.Muted)
                if (previousNote != null) {
                    AssistChip(onClick = { text = previousNote }, label = { Text("La vez pasada: $previousNote") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        FilterChip(
                            selected = text.equals(s, ignoreCase = true),
                            onClick = { text = s },
                            label = { Text(s) }
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (onSkip != null) TextButton(onClick = onSkip) { Text("Sin nota") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun FinishedCard(sessionActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.TintGreen)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("🎉 ¡Entregaste todas las facturas!", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (sessionActive) {
                Text(
                    "Toca «Terminar» arriba para guardar tu recorrido y el orden de hoy.",
                    color = AppColors.Muted, fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun NearbyBanner(near: Nearby, onDeliver: () -> Unit, onMap: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.TintViolet)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("A ${Geo.formatDistance(near.meters)} · NIR ${near.record.nir}", fontSize = 13.sp, color = AppColors.Muted)
                Text(near.record.user, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onMap, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Mapa") }
            Button(
                onClick = onDeliver,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Delivered),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Entregar", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DeliveredRow(d: Delivery, onUndo: () -> Unit, onMap: () -> Unit, onNote: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.Card)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${d.order}",
                fontWeight = FontWeight.Black, fontSize = 20.sp, color = AppColors.Delivered,
                modifier = Modifier.width(56.dp)
            )
            Column(Modifier.weight(1f)) {
                Text("NIR ${d.nir} · ${TimeUtil.hour(d.deliveredAt)}" + if (d.bulk != 0L) " · paquete" else "", fontWeight = FontWeight.Bold)
                Text(d.user, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    d.address + if (d.lat == null) " · sin GPS" else "",
                    fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (d.note.isNotBlank()) {
                    Text(
                        "Dejada: ${d.note}",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.Warning,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onNote) { Text(if (d.note.isBlank()) "+ Nota" else "Nota") }
                Row {
                    TextButton(onClick = onMap) { Text("Mapa") }
                    TextButton(onClick = onUndo) { Text("Deshacer", color = AppColors.Error) }
                }
            }
        }
    }
}
