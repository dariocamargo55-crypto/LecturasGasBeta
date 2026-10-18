package com.lecturasgas.beta.readings

import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedButton
import com.lecturasgas.beta.ui.icons.AppIcons
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.lecturasgas.beta.data.model.SearchField
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.ui.MainViewModel
import com.lecturasgas.beta.ui.components.EmptyState
import com.lecturasgas.beta.ui.components.ProximityBanner
import com.lecturasgas.beta.ui.components.MeterCard
import com.lecturasgas.beta.ui.components.ProgressStrip
import com.lecturasgas.beta.ui.components.RouteTopCard
import com.lecturasgas.beta.ui.components.SearchPanel
import com.lecturasgas.beta.ui.components.Segmented
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.TimeUtil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingsScreen(
    main: MainViewModel,
    onImport: () -> Unit,
    vm: ReadingsViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val field by vm.field.collectAsStateWithLifecycle()
    val numeric by vm.numeric.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val loaded by main.loaded.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val searchFocus by main.searchFocus.collectAsStateWithLifecycle()
    val records by main.repo.records.collectAsStateWithLifecycle()
    val route by main.route.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDashboard by remember { mutableStateOf(false) }
    val near by main.near.collectAsStateWithLifecycle()
    val settings by main.settings.collectAsStateWithLifecycle()
    val suspectCount = main.suspects.collectAsStateWithLifecycle().value.size
    val improving by main.improving.collectAsStateWithLifecycle()
    val imeVisible = WindowInsets.isImeVisible     // teclado abierto: se libera espacio arriba
    val routeSession by main.repo.session.collectAsStateWithLifecycle()
    val readToday = remember(records) {
        val start = Dashboard.startOfDay(System.currentTimeMillis())
        records.count { it.appReading != null && (it.readAt ?: 0L) >= start }
    }

    // Después de guardar una lectura (modo "Buscar otro"): buscador limpio y teclado listo.
    LaunchedEffect(searchFocus) {
        if (searchFocus > 0) vm.setQuery("")
    }

    if (!ui.hasRoute) {
        if (loaded) {
            EmptyState(
                title = "No hay una ruta cargada",
                body = "Importa el Excel que te entrega la empresa (por ejemplo \"Lecturas A sep.xlsx\"). " +
                        "La app lo guarda y podrás trabajar sin internet.",
                action = "Importar Excel",
                onAction = onImport
            )
        }
        return
    }

    if (showDashboard) {
        val now = System.currentTimeMillis()
        val stats = remember(records) { Dashboard.compute(records, now) }
        val routeName = route?.name ?: "Ruta"
        DashboardDialog(
            routeName = routeName,
            stats = stats,
            onShowPending = { vm.setQuery(""); vm.setFilter(ReadingFilter.PENDING); showDashboard = false },
            onShowSkipped = { vm.setQuery(""); vm.setFilter(ReadingFilter.SKIPPED); showDashboard = false },
            onShowBarrio = { name ->
                vm.showBarrio(name)
                showDashboard = false
            },
            onShare = {
                val date = java.text.SimpleDateFormat("d MMM", java.util.Locale("es", "CO")).format(java.util.Date(now))
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, Dashboard.summaryText(routeName, stats, date))
                }
                runCatching { context.startActivity(Intent.createChooser(send, "Compartir resumen")) }
            },
            onClose = { showDashboard = false },
            suspects = suspectCount,
            onReviewSuspects = { showDashboard = false; main.openSuspects() }
        )
    }

    // Número de sector (1, 2, 3…) según el orden de la lista.
    val sectorNumbers = remember(ui.items) {
        val m = HashMap<String, Int>()
        ui.items.forEach { if (it is ReadingItem.Header) m[it.id] = m.size + 1 }
        m
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!imeVisible) {
                val pct = if (ui.total == 0) 0 else (ui.read * 100 / ui.total)
                RouteTopCard(
                    fraction = if (ui.total == 0) 0f else ui.read.toFloat() / ui.total,
                    percent = pct, color = AppColors.Done,
                    done = ui.read, total = ui.total, pending = ui.total - ui.read,
                    extraPill = if (ui.skippedCount > 0) "${ui.skippedCount} con motivo" else null,
                    extraColor = AppColors.Skipped,
                    detail = "Hoy $readToday" + if (ui.withoutGps > 0) " · ${ui.withoutGps} sin GPS" else "",
                    session = routeSession,
                    onStart = { main.startRoute { main.showMessage(it) } },
                    onEnd = { main.endRoute() },
                    onOpenSummary = { showDashboard = true },
                    onOrder = { main.openReadingOrder() }
                )
            }
            // "Estás frente a NIR 104 [LEER]" (se esconde mientras escribes, para dar espacio).
            if (!imeVisible && !ui.searching) {
                near?.let { n ->
                    ProximityBanner(
                        near = n,
                        improving = improving == n.record.row,
                        onRead = { main.openReading(n.record.row) },
                        onImprove = { main.improveLocation(n.record.row) },
                        onDismiss = { main.dismissNear(n.record.row) },
                        vibrate = settings.nearVibrate
                    )
                }
            }
            SearchPanel(
                query = query,
                onQueryChange = vm::setQuery,
                fields = field,
                onFieldToggle = vm::toggleField,
                numeric = numeric,
                onNumericChange = vm::setNumeric,
                resultText = if (ui.searching) "${ui.resultCount} resultado(s)" else null,
                focusSignal = searchFocus,
                footer = {
                    ui.lastRead?.let { last ->
                        LastReadingLine(last) { main.openReading(last.row) }
                    }
                }
            )
            if (!ui.searching && !imeVisible) {
                Segmented(
                    options = ReadingFilter.entries.map { it to it.label },
                    selected = filter,
                    onSelect = vm::setFilter
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 64.dp, top = 4.dp)
            ) {
                items(
                    items = ui.items,
                    key = { item ->
                        when (item) {
                            is ReadingItem.Header -> "h_${item.id}"
                            is ReadingItem.Meter -> "m_${item.record.row}"
                        }
                    },
                    contentType = { if (it is ReadingItem.Header) 0 else 1 }
                ) { item ->
                    when (item) {
                        is ReadingItem.Header -> GroupHeader(item, sectorNumbers[item.id] ?: 0) { vm.toggleGroup(item.id) }
                        is ReadingItem.Meter -> MeterCard(
                            record = item.record,
                            onClick = { main.openReading(item.record.row) },
                            actions = if (item.record.isRead && !item.record.hasLocation) null else ({
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (item.record.hasLocation) {
                                        OutlinedButton(
                                            onClick = { main.showOnMap(item.record.row) },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Icon(AppIcons.Pin, contentDescription = "Ver en el mapa", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    if (!item.record.isRead) {
                                        Button(
                                            onClick = { main.openReading(item.record.row) },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                                        ) { Text("Leer", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                                    }
                                }
                            })
                        )
                    }
                }
                if (ui.items.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (ui.searching) "Sin resultados" else "Nada por aquí",
                            body = if (ui.searching) "Prueba con otro campo de búsqueda o con los últimos dígitos del medidor."
                            else "No hay medidores en este filtro."
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recordatorio chiquito: cuál fue la última lectura que registraste.
 * Tócalo para abrir esa tarjeta otra vez (por si hay que corregirla).
 */
@Composable
private fun LastReadingLine(record: MeterRecord, onClick: () -> Unit) {
    val consumption = record.consumption
    val text = buildString {
        append("Última: ")
        append(record.displayMeter)
        if (record.user.isNotBlank()) append(" · ").append(record.user)
        append(" · lectura ").append(record.appReading)
        if (consumption != null) append(" (").append(if (consumption >= 0) "+" else "").append(consumption).append(")")
        record.readAt?.let { append(" · ").append(TimeUtil.ago(it)) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("↺", fontSize = 13.sp, color = AppColors.Muted)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = if (consumption != null && consumption < 0) AppColors.Error else AppColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GroupHeader(item: ReadingItem.Header, number: Int, onClick: () -> Unit) {
    val complete = item.read == item.total
    val tint = if (complete) AppColors.Done else MaterialTheme.colorScheme.primary
    val parts = item.title.split(" · ", limit = 2)
    val name = parts.first()
    val range = parts.getOrNull(1)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(AppColors.Card, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (complete) "✔" else number.toString(),
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (range != null) Text(range, fontSize = 12.sp, color = AppColors.Muted)
            LinearProgressIndicator(
                progress = { if (item.total == 0) 0f else item.read.toFloat() / item.total },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = tint,
                trackColor = tint.copy(alpha = 0.15f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${item.read}/${item.total}",
            fontWeight = FontWeight.Bold,
            color = if (complete) AppColors.Done else AppColors.Pending
        )
        Icon(
            AppIcons.Chevron, contentDescription = null, tint = AppColors.Muted,
            modifier = Modifier.padding(start = 6.dp).size(18.dp).rotate(if (item.expanded) 90f else 0f)
        )
    }
}
