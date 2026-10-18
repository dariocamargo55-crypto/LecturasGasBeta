@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lecturasgas.beta.dashboard

import androidx.compose.foundation.background
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.components.ProgressRing
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.delivery.DeliveryDashboard
import com.lecturasgas.beta.readings.Dashboard
import com.lecturasgas.beta.readings.ReadingFilter
import com.lecturasgas.beta.readings.ReadingsViewModel
import com.lecturasgas.beta.ui.MainViewModel
import com.lecturasgas.beta.ui.MainViewModel.Companion.PAGE_DELIVERY
import com.lecturasgas.beta.ui.MainViewModel.Companion.PAGE_MAP
import com.lecturasgas.beta.ui.MainViewModel.Companion.PAGE_READINGS
import com.lecturasgas.beta.ui.components.EmptyState
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.TimeUtil
import com.lecturasgas.beta.review.ExportReview
import androidx.compose.foundation.layout.IntrinsicSize

/**
 * Módulo Inicio: cómo va la ruta de un vistazo, en lecturas y en entregas.
 * Cada tarjeta se puede tocar para ir directo a esos medidores.
 */
@Composable
fun DashboardScreen(
    main: MainViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    readingsVm: ReadingsViewModel = viewModel()
) {
    val records by main.repo.records.collectAsStateWithLifecycle()
    val deliveries by main.repo.deliveries.collectAsStateWithLifecycle()
    val skippedNow by main.repo.skippedForNow.collectAsStateWithLifecycle()
    val session by main.repo.session.collectAsStateWithLifecycle()
    val route by main.route.collectAsStateWithLifecycle()
    val unexported by main.unexported.collectAsStateWithLifecycle()
    val suspects by main.suspects.collectAsStateWithLifecycle()
    val loaded by main.loaded.collectAsStateWithLifecycle()
    val backup by main.backupStatus.collectAsStateWithLifecycle()

    if (route == null || records.isEmpty()) {
        if (loaded) {
            EmptyState(
                title = "Aún no hay una ruta",
                body = "Importa el Excel de la ruta y aquí verás cómo van las lecturas y las entregas.",
                action = "Importar Excel",
                onAction = onImport
            )
        }
        return
    }

    val now = System.currentTimeMillis()
    val rs = remember(records) { Dashboard.compute(records, now) }
    val ds = remember(records, deliveries, skippedNow) { DeliveryDashboard.compute(records, deliveries, skippedNow, now) }

    fun goReadings(f: ReadingFilter) { readingsVm.setQuery(""); readingsVm.setFilter(f); main.goToPage(PAGE_READINGS) }

    val deliveryNotes = remember(deliveries) {
        deliveries.filter { it.note.isNotBlank() }.sortedByDescending { it.deliveredAt }
    }
    val readingNotes = remember(records) {
        records.filter { it.appReading != null && it.note.isNotBlank() }.sortedByDescending { it.readAt ?: 0L }
    }
    val skipReasons = remember(records) {
        records.filter { it.isSkipped }.groupingBy { it.skipReason.orEmpty().trim().ifBlank { "Sin motivo" } }
            .eachCount().entries.sortedByDescending { it.value }
    }

    var dialog by remember { mutableStateOf<DialogSpec?>(null) }
    dialog?.let { d ->
        val (c, ic) = listStyle(d.title)
        ListDialog(d.title, d.hint, d.entries, c, ic) { dialog = null }
    }

    fun who(r: com.lecturasgas.beta.data.model.MeterRecord) = r.user.ifBlank { "Sin nombre" }
    fun place(r: com.lecturasgas.beta.data.model.MeterRecord) =
        listOf(r.address, r.barrio).filter { it.isNotBlank() }.joinToString(" · ")
    fun recordEntries(list: List<com.lecturasgas.beta.data.model.MeterRecord>, extra: (com.lecturasgas.beta.data.model.MeterRecord) -> String = { "" },
                      click: (com.lecturasgas.beta.data.model.MeterRecord) -> Unit) =
        list.sortedBy { it.nirNumber ?: Long.MAX_VALUE }.map { r ->
            ListEntry("NIR ${r.nir} · ${r.displayMeter}", who(r) + (place(r).takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), extra(r)) { click(r) }
        }
    val deliveredRows = deliveries.map { it.row }.toHashSet()

    fun barrioEntries(list: List<com.lecturasgas.beta.readings.BarrioStat>, unit: String, click: (String) -> Unit) =
        list.sortedWith(compareByDescending<com.lecturasgas.beta.readings.BarrioStat> { it.pending }.thenBy { it.name }).map { b ->
            ListEntry(b.name, if (b.pending == 0) "Terminado ✓ (${b.total})" else "$unit ${b.read} de ${b.total} · faltan ${b.pending}") { click(b.name) }
        }

    // ---------- Alertas (tarjetas de «Requiere atención») ----------
    // Consumos altos / en cero: mismos umbrales y mismo análisis que la pantalla «Revisión antes de exportar».
    val review = remember(records) { ExportReview.analyze(records) }
    val reviewEntries = { kind: ExportReview.Kind ->
        review.section(kind)?.items.orEmpty().map { it ->
            ListEntry("NIR ${it.nir} · ${it.meter}", it.user.ifBlank { "Sin nombre" }, it.detail) { main.openReading(it.row) }
        }
    }
    val alerts = buildList<AlertItem> {
        if (rs.negatives > 0) add(AlertItem("${rs.negatives}", "Menores", "Lectura menor a la anterior", AttentionColors.red, AppIcons.Warning, urgent = true) {
            dialog = DialogSpec("Lecturas menores a la anterior", "Toca una para revisarla o corregirla.",
                recordEntries(records.filter { (it.consumption ?: 0L) < 0L }, { r -> "Lectura ${r.appReading ?: r.excelReading} · anterior ${r.previous}" }) { r -> main.openReading(r.row) })
        })
        review.count(ExportReview.Kind.HIGH).let { n ->
            if (n > 0) add(AlertItem("$n", "Consumo muy alto", "Posibles fugas o un dígito de más", AttentionColors.orange, AppIcons.Trend, urgent = true) {
                dialog = DialogSpec("Consumo muy alto",
                    "Mucho mayor que lo normal de esta ruta" + (review.typicalConsumption?.let { " (lo típico es ≈ $it)" } ?: "") + ". Toca uno para revisarlo.",
                    reviewEntries(ExportReview.Kind.HIGH))
            })
        }
        if (suspects.isNotEmpty()) add(AlertItem("${suspects.size}", "Ubicación sospechosa", "El punto no parece estar bien", AttentionColors.amber, AppIcons.Locate, urgent = true) { main.openSuspects() })
        review.count(ExportReview.Kind.ZERO).let { n ->
            if (n > 0) add(AlertItem("$n", "Consumo cero", "Lectura igual a la anterior", AttentionColors.teal, AppIcons.Zero) {
                dialog = DialogSpec("Consumo cero", "La lectura es igual a la anterior: casa vacía, medidor parado o lectura repetida. Toca uno para revisarlo.",
                    reviewEntries(ExportReview.Kind.ZERO))
            })
        }
        if (rs.withoutGps > 0) add(AlertItem("${rs.withoutGps}", "Sin ubicación", "Falta su punto en el mapa", AttentionColors.amber, AppIcons.Pin) {
            dialog = DialogSpec("Medidores sin ubicación", "Toca uno para ponerle su punto en el mapa.",
                recordEntries(records.filter { !it.hasLocation }) { r -> main.adjustOnMap(r.row) })
        })
        if (ds.pendingWithoutGps > 0) add(AlertItem("${ds.pendingWithoutGps}", "Por entregar sin GPS", "Factura pendiente y sin punto", AttentionColors.amber, AppIcons.Box) {
            dialog = DialogSpec("Por entregar y sin ubicación", "Toca uno para ponerle su punto en el mapa.",
                recordEntries(records.filter { it.row !in deliveredRows && !it.hasLocation }) { r -> main.adjustOnMap(r.row) })
        })
        if (rs.newPending > 0) add(AlertItem("${rs.newPending}", "Nuevos sin leer", "No estaban en el Excel anterior", AttentionColors.purple, AppIcons.Plus) {
            dialog = DialogSpec("Medidores nuevos sin leer", "Usuarios nuevos (no estaban en el Excel anterior) que aún no tienen lectura. Toca uno para leerlo.",
                recordEntries(records.filter { it.isNew && !it.isRead }) { r -> main.openReading(r.row) })
        })
        if (rs.meterFixes > 0) add(AlertItem("${rs.meterFixes}", "Número corregido", "Distinto al del Excel", AttentionColors.blue, AppIcons.Pencil) {
            dialog = DialogSpec("Medidores con número corregido", "Número que escribiste vs. el que traía el Excel. Toca uno para abrirlo.",
                recordEntries(records.filter { it.hasMeterFix }, { r -> "Excel decía: ${r.meter}" }) { r -> main.openReading(r.row) })
        })
        if (rs.skipped > 0) add(AlertItem("${rs.skipped}", "Con motivo", "No se pudieron leer", AttentionColors.brown, AppIcons.ListLines) { goReadings(ReadingFilter.SKIPPED) })
        if (ds.skipped > 0) add(AlertItem("${ds.skipped}", "Para después", "Entregas que dejaste para más tarde", AttentionColors.brown, AppIcons.Clock) {
            dialog = DialogSpec("Entregas para después", "Las dejaste para más tarde. Toca para ir a Entrega.",
                recordEntries(records.filter { it.row in skippedNow && it.row !in deliveredRows }) { _ -> main.goToPage(PAGE_DELIVERY) })
        })
        if (rs.pending > 0) add(AlertItem("${rs.pending}", "Sin leer", "Todavía no tienen lectura", AttentionColors.slate, AppIcons.Eye) { goReadings(ReadingFilter.PENDING) })
    }

    val openReadingsList = {
        val head = ListEntry("Ver las ${rs.pending} lecturas pendientes ›", "Abre Lecturas con el filtro «Pendientes»") { goReadings(ReadingFilter.PENDING) }
        dialog = DialogSpec(
            "Lecturas por barrio", "${rs.read} de ${rs.total} leídas (${rs.percent}%). Toca un barrio para ver sus medidores.",
            listOf(head) + barrioEntries(rs.barrios, "Leídas") { name -> readingsVm.showBarrio(name); main.goToPage(PAGE_READINGS) }
        )
    }
    val openDeliveryList = {
        val head = ListEntry("Ir a Entrega ›", "Faltan ${ds.pending} facturas por entregar") { main.goToPage(PAGE_DELIVERY) }
        dialog = DialogSpec(
            "Entregas por barrio", "${ds.delivered} de ${ds.total} entregadas (${ds.percent}%).",
            listOf(head) + barrioEntries(ds.barrios, "Entregadas") { _ -> main.goToPage(PAGE_DELIVERY) }
        )
    }

    // Detalle interactivo de cada anillo (0 = lecturas, 1 = entregas).
    var detail by remember { mutableStateOf<Int?>(null) }
    val readWeek = remember(records) { weekCounts(records.mapNotNull { if (it.appReading != null) it.readAt else null }) }
    val deliveryWeek = remember(deliveries) { weekCounts(deliveries.map { it.deliveredAt }) }
    when (detail) {
        0 -> RouteDetailDialog(
            title = "Lecturas", unit = "leídas", color = AppColors.Done,
            done = rs.read, total = rs.total, today = rs.readToday, perHour = rs.perHour, etaMinutes = rs.etaMinutes,
            week = readWeek, barrios = rs.barrios,
            primaryLabel = "Ver las ${rs.pending} lecturas pendientes ›",
            onPrimary = { detail = null; goReadings(ReadingFilter.PENDING) },
            onBarrio = { name -> detail = null; readingsVm.showBarrio(name); main.goToPage(PAGE_READINGS) },
            onClose = { detail = null }
        )
        1 -> RouteDetailDialog(
            title = "Entregas", unit = "entregadas", color = AppColors.Delivered,
            done = ds.delivered, total = ds.total, today = ds.deliveredToday, perHour = ds.perHour, etaMinutes = ds.etaMinutes,
            week = deliveryWeek, barrios = ds.barrios,
            primaryLabel = "Ir a Entrega · faltan ${ds.pending} ›",
            onPrimary = { detail = null; main.goToPage(PAGE_DELIVERY) },
            onBarrio = { _ -> detail = null; main.goToPage(PAGE_DELIVERY) },
            onClose = { detail = null }
        )
        else -> Unit
    }

    var notesOpen by remember { mutableStateOf(false) }
    var reasonsOpen by remember { mutableStateOf(false) }
    var barriosOpen by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- Encabezado: ruta, fecha, respaldo y exportar ----------
        item {
            val warn = backup.folderName == null || backup.lastError != null
            val backupText = when {
                backup.lastError != null -> "El respaldo automático falló · toca para arreglar"
                backup.folderName == null -> "Respaldo solo en el celular · elige una carpeta"
                else -> "Respaldo automático activo" +
                        if (backup.lastAutoAt > 0L) " · ${TimeUtil.dateTime(backup.lastAutoAt)}" else ""
            }
            RouteHeader(
                modifier = Modifier.appear(0),
                name = route?.name ?: "Ruta",
                meters = rs.total,
                dateText = java.text.SimpleDateFormat("EEEE d 'de' MMMM", java.util.Locale("es", "CO")).format(java.util.Date(now))
                    .replaceFirstChar { it.uppercase() },
                backupText = backupText,
                backupWarn = warn,
                unexported = unexported,
                onExport = onExport,
                onBackup = { main.openBackup() }
            )
        }

        // ---------- Avance de la ruta: dos anillos + hoy ----------
        item {
            Column(
                Modifier.fillMaxWidth().appear(1).background(AppColors.Card, RoundedCornerShape(22.dp)).padding(16.dp)
            ) {
                Text("AVANCE DE LA RUTA", fontSize = 13.sp, fontWeight = FontWeight.Black, color = AppColors.Muted, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RingColumn("Lecturas", rs.read, rs.total, AppColors.Done, "Faltan ${rs.pending}", Modifier.weight(1f)) { detail = 0 }
                    Box(Modifier.width(1.dp).height(140.dp).background(AppColors.Divider))
                    RingColumn("Entregas", ds.delivered, ds.total, AppColors.Delivered, "Faltan ${ds.pending}", Modifier.weight(1f)) { detail = 1 }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
                Spacer(Modifier.height(12.dp))
                Text("HOY", fontSize = 13.sp, fontWeight = FontWeight.Black, color = AppColors.Muted, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    MiniStat("${rs.readToday}", "lecturas", AppColors.Done, Modifier.weight(1f))
                    MiniStat("${ds.deliveredToday}", "entregas", AppColors.Delivered, Modifier.weight(1f))
                    MiniStat(rs.perHour?.let { "${it.toInt()}/h" } ?: "—", "ritmo lect.", AppColors.Pending, Modifier.weight(1f))
                    MiniStat(ds.perHour?.let { "${it.toInt()}/h" } ?: "—", "ritmo entr.", AppColors.Accent, Modifier.weight(1f))
                }
                if (rs.etaMinutes != null || ds.etaMinutes != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildString {
                            rs.etaMinutes?.let { append("Lecturas: faltan unos ").append(Dashboard.formatMinutes(it)).append(". ") }
                            ds.etaMinutes?.let { append("Entregas: faltan unos ").append(Dashboard.formatMinutes(it)).append(".") }
                        },
                        fontSize = 13.sp, color = AppColors.Muted
                    )
                }
            }
        }

        // ---------- Jornada de entrega ----------
        session?.let { s ->
            item {
                Row(
                    Modifier.fillMaxWidth().appear(2)
                        .background(AppColors.TintGreen, RoundedCornerShape(16.dp))
                        .clickable { main.goToPage(PAGE_DELIVERY) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pulse by rememberPulse(0.35f, 1f, 900)
                    Box(Modifier.size(12.dp).graphicsLayer { alpha = pulse; scaleX = 0.8f + 0.4f * pulse; scaleY = 0.8f + 0.4f * pulse }.background(AppColors.Done, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Jornada de entrega en curso", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Empezó a las ${TimeUtil.hour(s.startedAt)} · ${"%.1f".format(java.util.Locale.US, s.distanceMeters / 1000.0)} km recorridos",
                            fontSize = 13.sp, color = AppColors.Muted
                        )
                    }
                    Icon(AppIcons.Chevron, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(22.dp))
                }
            }
        }

        // ---------- Requiere atención ----------
        item { SectionTitle("REQUIERE ATENCIÓN", Modifier.appear(3)) }
        item {
            if (alerts.isEmpty()) {
                CardBox { Text("Todo en orden ✓", color = AppColors.Done, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    alerts.chunked(2).forEachIndexed { rowIndex, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                            pair.forEachIndexed { i, a -> AttentionCard(a, rowIndex * 2 + i, Modifier.weight(1f).fillMaxHeight()) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ---------- Notas ----------
        if (deliveryNotes.isNotEmpty() || readingNotes.isNotEmpty()) {
            item {
                Collapsible("Notas", "${deliveryNotes.size + readingNotes.size}", notesOpen, { notesOpen = !notesOpen }, AppIcons.ListLines, Color(0xFF0B7A8A), 6) {
                    if (deliveryNotes.isNotEmpty()) {
                        Text("Dónde dejaste la factura (${deliveryNotes.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        deliveryNotes.take(4).forEach { d ->
                            NoteLine(d.note, "NIR ${d.nir}" + if (d.user.isNotBlank()) " · ${d.user}" else "")
                        }
                        if (deliveryNotes.size > 4) SeeAll("Ver todas (${deliveryNotes.size})") {
                            dialog = DialogSpec("Dónde dejaste las facturas", "Las más recientes primero.",
                                deliveryNotes.map { d ->
                                    ListEntry(d.note, "NIR ${d.nir}" + (if (d.user.isNotBlank()) " · ${d.user}" else "") + (if (d.address.isNotBlank()) " · ${d.address}" else ""),
                                        TimeUtil.dateTime(d.deliveredAt))
                                })
                        }
                    }
                    if (readingNotes.isNotEmpty()) {
                        Text("Notas de lecturas (${readingNotes.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.padding(top = if (deliveryNotes.isNotEmpty()) 14.dp else 0.dp))
                        readingNotes.take(3).forEach { r ->
                            NoteLine(r.note, "NIR ${r.nir}" + if (r.user.isNotBlank()) " · ${r.user}" else "") {
                                main.openReading(r.row)
                            }
                        }
                        if (readingNotes.size > 3) SeeAll("Ver todas (${readingNotes.size})") {
                            dialog = DialogSpec("Notas de lecturas", "Toca una para abrir el medidor.",
                                readingNotes.map { r ->
                                    ListEntry(r.note, "NIR ${r.nir}" + (if (r.user.isNotBlank()) " · ${r.user}" else ""),
                                        r.readAt?.let { TimeUtil.dateTime(it) } ?: "") { main.openReading(r.row) }
                                })
                        }
                    }
                }
            }
        }

        // ---------- Motivos de no lectura ----------
        if (skipReasons.isNotEmpty()) {
            item {
                Collapsible("Motivos de no lectura", "${skipReasons.sumOf { it.value }}", reasonsOpen, { reasonsOpen = !reasonsOpen }, AppIcons.Clock, AppColors.Skipped, 7) {
                    val maxN = skipReasons.maxOf { it.value }.coerceAtLeast(1)
                    skipReasons.take(6).forEach { (reason, n) ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(reason, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            var go by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { go = true }
                            val w by androidx.compose.animation.core.animateFloatAsState(if (go) n.toFloat() / maxN else 0f, androidx.compose.animation.core.tween(700), label = "motivo")
                            Box(Modifier.width(80.dp).height(6.dp).background(AppColors.RingTrack, RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(w).height(6.dp).background(AppColors.Skipped, RoundedCornerShape(3.dp)))
                            }
                            Text("$n", Modifier.width(36.dp), fontWeight = FontWeight.Black, color = AppColors.Skipped, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }

        // ---------- Barrios ----------
        val topLect = rs.barrios.filter { it.pending > 0 }.take(6)
        val topEnt = ds.barrios.filter { it.pending > 0 }.take(6)
        if (topLect.isNotEmpty() || topEnt.isNotEmpty()) {
            item {
                Collapsible("Barrios con más pendientes", "${rs.barrios.size}", barriosOpen, { barriosOpen = !barriosOpen }, AppIcons.Pin, AppColors.Pending, 8) {
                    if (topLect.isNotEmpty()) {
                        Text("Lecturas", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        topLect.forEach { b ->
                            BarrioRow(b.name, b.read, b.total, AppColors.Done) { readingsVm.showBarrio(b.name); main.goToPage(PAGE_READINGS) }
                        }
                        SeeAll("Ver los ${rs.barrios.size} barrios (lecturas)", openReadingsList)
                    }
                    if (topEnt.isNotEmpty()) {
                        Text("Entregas", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                        topEnt.forEach { b ->
                            BarrioRow(b.name, b.read, b.total, AppColors.Delivered) { main.goToPage(PAGE_DELIVERY) }
                        }
                        SeeAll("Ver los ${ds.barrios.size} barrios (entregas)", openDeliveryList)
                    }
                }
            }
        }
    }
}

private class DialogSpec(val title: String, val hint: String, val entries: List<ListEntry>)

/** Color e icono de cada lista según de qué trata. */
private fun listStyle(title: String): Pair<Color, androidx.compose.ui.graphics.vector.ImageVector?> {
    val t = title.lowercase()
    return when {
        "consumo muy alto" in t -> AttentionColors.orange to AppIcons.Trend
        "consumo cero" in t -> AttentionColors.teal to AppIcons.Zero
        "menores" in t -> Color(0xFFC62828) to AppIcons.Warning
        "nuevos" in t -> Color(0xFF7B3FE4) to AppIcons.Plus
        "sin ubicación" in t -> Color(0xFFB26A00) to AppIcons.Pin
        "número corregido" in t -> Color(0xFF0B4F8A) to AppIcons.Pencil
        "para después" in t -> Color(0xFF8D6E63) to AppIcons.Clock
        "dónde dejaste" in t -> Color(0xFF2E7D32) to AppIcons.Box
        "notas" in t -> Color(0xFF0B4F8A) to AppIcons.ListLines
        "entregas por barrio" in t -> Color(0xFFD64545) to AppIcons.Box
        "lecturas por barrio" in t -> Color(0xFF2E7D32) to AppIcons.ListLines
        else -> Color(0xFF0B4F8A) to null
    }
}

internal class AlertItem(
    val count: String, val short: String, val hint: String, val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val urgent: Boolean = false, val onClick: () -> Unit
)

@Composable
private fun SeeAll(text: String, onClick: () -> Unit) {
    Text(
        text, fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClick)
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Black, color = AppColors.Muted, letterSpacing = 1.2.sp, modifier = modifier.padding(top = 4.dp))
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(AppColors.Card, RoundedCornerShape(16.dp)).padding(16.dp)
    ) { content() }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

/** Encabezado de Inicio: nombre de la ruta, cuántos medidores, fecha, estado del respaldo y un botón de exportar. */
@Composable
private fun RouteHeader(
    modifier: Modifier = Modifier,
    name: String, meters: Int, dateText: String,
    backupText: String, backupWarn: Boolean, unexported: Int,
    onExport: () -> Unit, onBackup: () -> Unit
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = AppColors.TopBar, contentColor = Color.White, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeaderPill("$meters medidores")
                        HeaderPill(dateText)
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Un solo botoncito: exportar el Excel (el número es lo que falta por exportar).
                Box {
                    Surface(
                        onClick = onExport,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(Modifier.fillMaxSize().semantics { contentDescription = "Exportar a Excel" }, contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Export, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                    if (unexported > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).background(AppColors.Accent, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp)
                        ) { Text(if (unexported > 99) "99+" else "$unexported", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBackup).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).background(if (backupWarn) Color(0xFFFFB74D) else Color(0xFF7CE0A0), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(backupText, fontSize = 13.sp, color = Color.White.copy(alpha = 0.88f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HeaderPill(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
        modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Un anillo con su título, «x de y» y lo que falta. Al tocarlo abre la lista por barrio. */
@Composable
private fun RingColumn(
    title: String, done: Int, total: Int, color: Color, line: String,
    modifier: Modifier, onClick: () -> Unit
) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val k by androidx.compose.animation.core.animateFloatAsState(if (go) 1f else 0f, androidx.compose.animation.core.tween(900), label = "pct")
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProgressRing(fraction = fraction, color = color, size = 108.dp, strokeWidth = 9.dp) {
            Text("${(fraction * 100 * k).toInt()}%", fontSize = 27.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text("${(done * k).toInt()} de $total", fontSize = 14.sp, color = AppColors.Muted)
        Text(line, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
        Text("Ver detalle ›", fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MiniStat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
        Text(label, fontSize = 13.sp, color = AppColors.Muted, maxLines = 1)
    }
}

/** Tarjeta que se despliega al tocar el título (Notas, Motivos, Barrios), con icono de color. */
@Composable
private fun Collapsible(
    title: String, count: String, expanded: Boolean, onToggle: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, index: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    val rot by androidx.compose.animation.core.animateFloatAsState(if (expanded) 90f else 0f, label = "chev")
    Column(
        Modifier.fillMaxWidth().appear(index, 70L)
            .background(AppColors.Card, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).animateContentSize()
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onToggle).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).background(color.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                count, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color,
                modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.Chevron, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(20.dp).rotate(rot))
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), content = content)
        }
    }
}

@Composable
private fun NoteLine(note: String, who: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.TintViolet.copy(alpha = 0.55f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(AppColors.Pending))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("“$note”", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(who, fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BarrioRow(name: String, done: Int, total: Int, color: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 10.dp)) {
        Row {
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$done/$total", fontWeight = FontWeight.Bold, color = AppColors.Muted, fontSize = 13.sp)
        }
        var go by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { go = true }
        val shownP by androidx.compose.animation.core.animateFloatAsState(
            if (go && total > 0) done.toFloat() / total else 0f,
            androidx.compose.animation.core.tween(700), label = "barrio"
        )
        LinearProgressIndicator(
            progress = { shownP },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color, trackColor = AppColors.RingTrack
        )
    }
}
