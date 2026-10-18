package com.lecturasgas.beta.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.dashboard.appear
import com.lecturasgas.beta.dashboard.rememberPulse
import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.data.model.SessionSummary
import com.lecturasgas.beta.review.HistoryStats
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.Geo
import com.lecturasgas.beta.util.TimeUtil
import java.util.Locale

/**
 * Historial de jornadas: pantalla completa. Arriba, los totales; debajo, las jornadas agrupadas por mes
 * en tarjetas (fecha, ruta, entregas, distancia, tiempo y ritmo). Al tocar una se ve el orden real de entrega.
 * Las jornadas se guardan en el celular sin límite de tiempo; solo se borran al borrar su ruta.
 */
@Composable
fun HistoryDialog(list: List<SessionSummary>, main: MainViewModel, onClose: () -> Unit) {
    var open by remember { mutableStateOf<SessionSummary?>(null) }
    var detail by remember { mutableStateOf<List<Delivery>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        detail = emptyList()
        val current = open
        if (current != null) {
            loading = true
            detail = main.sessionDeliveries(current.session.id)
            loading = false
        }
    }

    Dialog(
        // Atrás: del detalle vuelve a la lista; desde la lista cierra.
        onDismissRequest = { if (open != null) open = null else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            AnimatedContent(
                targetState = open,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it / 6 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(280)) { -it / 6 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(200)))
                    }
                },
                label = "historial"
            ) { current ->
                if (current == null) {
                    HistoryList(list, onOpen = { open = it }, onClose = onClose)
                } else {
                    SessionDetail(current, detail, loading, onBack = { open = null })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Lista

@Composable
private fun HistoryList(list: List<SessionSummary>, onOpen: (SessionSummary) -> Unit, onClose: () -> Unit) {
    val totals = remember(list) { HistoryStats.totals(list) }
    val months = remember(list) { HistoryStats.byMonth(list) }
    val maxDeliveries = remember(list) { (list.maxOfOrNull { it.deliveries } ?: 1).coerceAtLeast(1) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = AppColors.TopBar, contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 16.dp)) {
                TextButton(onClick = onClose) { Text("‹ Cerrar", color = Color.White, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Historial de jornadas", fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(
                        if (list.isEmpty()) "Aquí verás cada jornada de entrega que hagas"
                        else "${totals.sessions} jornadas en ${totals.days} ${if (totals.days == 1) "día" else "días"}",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
                    )
                }
                if (list.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Kpi("${totals.deliveries}", "entregas", Modifier.weight(1f))
                        Kpi(if (totals.meters > 0) Geo.formatDistance(totals.meters) else "—", "recorridos", Modifier.weight(1f))
                        Kpi(if (totals.millis > 0) TimeUtil.duration(totals.millis) else "—", "en la calle", Modifier.weight(1f))
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(84.dp).background(AppColors.Brand.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Clock, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Todavía no hay jornadas guardadas", fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Inicia una desde el módulo Entrega. Cada jornada queda guardada con su fecha, distancia, tiempo y el orden en que entregaste.",
                    fontSize = 14.sp, color = AppColors.Muted, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                var index = 0
                for ((month, sessions) in months) {
                    item(key = "m_$month") {
                        Text(
                            month.uppercase(Locale.ROOT) + "  ·  ${sessions.size}",
                            fontSize = 13.sp, fontWeight = FontWeight.Black, color = AppColors.Muted, letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                        )
                    }
                    for (s in sessions) {
                        val i = index++
                        item(key = s.session.id) { SessionCard(s, maxDeliveries, i, onClick = { onOpen(s) }) }
                    }
                }
                item {
                    Text(
                        "Las jornadas se guardan en este celular sin límite de tiempo, también en las copias de seguridad. " +
                            "Solo se borran si borras la ruta a la que pertenecen.",
                        fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun Kpi(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun SessionCard(s: SessionSummary, maxDeliveries: Int, index: Int, onClick: () -> Unit) {
    val active = s.session.endedAt == null
    val millis = s.session.endedAt?.let { it - s.session.startedAt }
    val pace = millis?.let { HistoryStats.perHour(s.deliveries, it) }
    val accent = if (active) AppColors.Done else AppColors.Brand
    val pulse by rememberPulse(0.4f, 1f, 900)
    val shape = RoundedCornerShape(18.dp)

    Row(
        Modifier.fillMaxWidth().appear(minOf(index, 6), 50L)
            .clip(shape).background(AppColors.Card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ficha de fecha.
        Column(
            Modifier.width(56.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(TimeUtil.weekdayShort(s.session.startedAt).uppercase(Locale.ROOT), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(TimeUtil.dayNumber(s.session.startedAt), fontSize = 24.sp, fontWeight = FontWeight.Black, color = accent)
            Text(TimeUtil.monthShort(s.session.startedAt), fontSize = 11.sp, color = AppColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TimeUtil.hour(s.session.startedAt) + (s.session.endedAt?.let { " – " + TimeUtil.hour(it) } ?: ""),
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false)
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.background(AppColors.Done.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).graphicsLayer { alpha = pulse }.background(AppColors.Done, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text("EN CURSO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = AppColors.Done)
                    }
                }
            }
            Text(
                s.routeName.ifBlank { "Importada de la versión anterior" },
                fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Stat(AppIcons.Box, "${s.deliveries}", AppColors.Delivered)
                if (s.session.distanceMeters > 0) Stat(AppIcons.Pin, Geo.formatDistance(s.session.distanceMeters), AppColors.Brand)
                if (millis != null) Stat(AppIcons.Clock, TimeUtil.duration(millis), AppColors.Muted)
                if (pace != null) Stat(AppIcons.Trend, "${String.format(Locale("es", "CO"), "%.1f", pace)}/h", AppColors.Done)
            }
            Spacer(Modifier.height(8.dp))
            // Tamaño de la jornada frente a la más grande.
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(AppColors.RingTrack)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth((s.deliveries.toFloat() / maxDeliveries).coerceIn(0.03f, 1f))
                        .clip(RoundedCornerShape(50)).background(accent)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(AppIcons.Chevron, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Stat(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ------------------------------------------------------------------ Detalle

@Composable
private fun SessionDetail(s: SessionSummary, detail: List<Delivery>, loading: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val ordered = remember(detail) { detail.sortedBy { it.order } }
    val millis = s.session.endedAt?.let { it - s.session.startedAt }
    val pace = millis?.let { HistoryStats.perHour(s.deliveries, it) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = AppColors.TopBar, contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 16.dp)) {
                TextButton(onClick = onBack) { Text("‹ Volver", color = Color.White, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        TimeUtil.dateLong(s.session.startedAt).replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Black, fontSize = 20.sp
                    )
                    Text(
                        (s.routeName.ifBlank { "Importada de la versión anterior" }) +
                            "  ·  " + TimeUtil.hour(s.session.startedAt) + (s.session.endedAt?.let { " – " + TimeUtil.hour(it) } ?: "  ·  en curso"),
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("${s.deliveries}", "entregas", Modifier.weight(1f))
                    Kpi(if (s.session.distanceMeters > 0) Geo.formatDistance(s.session.distanceMeters) else "—", "recorridos", Modifier.weight(1f))
                    Kpi(
                        millis?.let { TimeUtil.duration(it) } ?: "—",
                        pace?.let { "${String.format(Locale("es", "CO"), "%.1f", it)} entregas/h" } ?: "duración",
                        Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            "ORDEN REAL DE ENTREGA",
            fontSize = 13.sp, fontWeight = FontWeight.Black, color = AppColors.Muted, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Text("Cargando…", color = AppColors.Muted, modifier = Modifier.align(Alignment.Center))
                ordered.isEmpty() -> Text(
                    "Esta jornada no tiene entregas guardadas.", color = AppColors.Muted,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center
                )
                else -> LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp)) {
                    items(ordered, key = { it.id }) { d -> TimelineRow(d, isLast = d === ordered.last()) }
                }
            }
        }

        Surface(color = AppColors.Card, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = 28.dp)))
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Button(
                    enabled = ordered.isNotEmpty(),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText(s, ordered, millis, pace))
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "Compartir orden de entrega")) }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartir lista", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** Una entrega en la línea de tiempo: círculo con el orden, línea que baja hasta la siguiente y los datos. */
@Composable
private fun TimelineRow(d: Delivery, isLast: Boolean) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).background(AppColors.Delivered, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${d.order}", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (d.order >= 100) 11.sp else 13.sp, maxLines = 1)
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(AppColors.Delivered.copy(alpha = 0.25f)))
        }
        Column(
            Modifier.weight(1f).padding(bottom = 10.dp)
                .background(AppColors.Card, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NIR ${d.nir}", fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(TimeUtil.hour(d.deliveredAt), fontSize = 13.sp, color = AppColors.Muted)
            }
            if (d.user.isNotBlank()) Text(d.user, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val place = listOf(d.address, d.barrio).filter { it.isNotBlank() }.joinToString(" · ")
            if (place.isNotBlank()) Text(place, fontSize = 13.sp, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.background(AppColors.Done.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Pin, contentDescription = null, tint = AppColors.Done, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(d.note, fontSize = 12.sp, color = AppColors.Done, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun shareText(s: SessionSummary, list: List<Delivery>, millis: Long?, pace: Double?): String = buildString {
    append("ORDEN DE ENTREGA\n")
    append(TimeUtil.dateLong(s.session.startedAt).replaceFirstChar { it.uppercase() })
    if (s.routeName.isNotBlank()) append(" · ").append(s.routeName)
    append('\n')
    append("${s.deliveries} entregas")
    if (s.session.distanceMeters > 0) append(" · ").append(Geo.formatDistance(s.session.distanceMeters))
    if (millis != null) append(" · ").append(TimeUtil.duration(millis))
    if (pace != null) append(" · ").append(String.format(Locale("es", "CO"), "%.1f", pace)).append(" entregas/h")
    append("\n\n")
    list.forEach { d ->
        append("${d.order}. ").append(TimeUtil.hour(d.deliveredAt)).append(" · NIR ").append(d.nir)
        if (d.user.isNotBlank()) append(" · ").append(d.user)
        append('\n')
        val place = listOf(d.address, d.barrio).filter { it.isNotBlank() }.joinToString(" · ")
        if (place.isNotBlank()) append("    ").append(place).append('\n')
        if (d.note.isNotBlank()) append("    📍 ").append(d.note).append('\n')
    }
}.trimEnd()
