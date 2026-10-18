package com.lecturasgas.beta.delivery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.lecturasgas.beta.ui.components.ProgressRing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.readings.Dashboard
import com.lecturasgas.beta.readings.ProgressCard
import com.lecturasgas.beta.readings.Section
import com.lecturasgas.beta.readings.Tile
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.Geo
import com.lecturasgas.beta.util.TimeUtil

/** Encabezado de avance de Entrega (misma tarjeta que Lecturas). Al tocarlo abre el resumen. */
@Composable
fun DeliveryHeader(stats: DeliveryStats, compact: Boolean, onClick: () -> Unit) {
    val parts = buildList {
        add("Hoy ${stats.deliveredToday}")
        stats.perHour?.let { add("${it.toInt()} por hora") }
        stats.etaMinutes?.let { add("≈ ${Dashboard.formatMinutes(it)} para terminar") }
    }
    ProgressCard(
        done = stats.delivered, total = stats.total, doneLabel = "entregadas", color = AppColors.Delivered,
        pending = stats.pending,
        detail = parts.joinToString(" · "),
        extra = if (stats.skipped > 0) "${stats.skipped} para después" else null,
        extraColor = AppColors.Skipped,
        compact = compact, onClick = onClick
    )
}

@Composable
fun DeliveryDashboardDialog(
    routeName: String,
    stats: DeliveryStats,
    sessionDistance: Double?,
    sessionDuration: String?,
    onShowPending: () -> Unit,
    onShowDone: () -> Unit,
    onIncludeSkipped: () -> Unit,
    onShowBarrio: (String) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(routeName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(fraction = stats.fraction, color = AppColors.Delivered, size = 92.dp, strokeWidth = 8.dp) {
                        Text("${stats.percent}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppColors.Delivered)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${stats.delivered}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = AppColors.Delivered)
                        Text("de ${stats.total} entregadas", fontSize = 15.sp)
                    }
                }

                Section("HOY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("${stats.deliveredToday}", "entregadas hoy", AppColors.Delivered, Modifier.weight(1f))
                    Tile(stats.perHour?.let { "${it.toInt()}/h" } ?: "—", "ritmo", AppColors.Pending, Modifier.weight(1f))
                    Tile(
                        stats.etaMinutes?.let { Dashboard.formatMinutes(it) } ?: "—",
                        "para terminar", AppColors.Accent, Modifier.weight(1f)
                    )
                }
                if (stats.perHour == null) {
                    Text(
                        "El ritmo y el tiempo estimado aparecen cuando llevas unas 4 entregas en 15 minutos.",
                        fontSize = 13.sp, color = AppColors.Muted
                    )
                } else if (stats.firstTodayAt != null && stats.lastTodayAt != null) {
                    Text(
                        "Primera entrega ${TimeUtil.hour(stats.firstTodayAt)} · última ${TimeUtil.hour(stats.lastTodayAt)}.",
                        fontSize = 13.sp, color = AppColors.Muted
                    )
                }
                if (sessionDistance != null && sessionDuration != null) {
                    Text(
                        "Recorrido de la jornada: ${Geo.formatDistance(sessionDistance)} en $sessionDuration.",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }

                Section("ESTADO DE LA RUTA")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("${stats.pending}", "por entregar", AppColors.Pending, Modifier.weight(1f), onClick = onShowPending)
                    Tile("${stats.delivered}", "entregadas", AppColors.Delivered, Modifier.weight(1f),
                        onClick = if (stats.delivered > 0) onShowDone else null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("${stats.skipped}", "para después", AppColors.Skipped, Modifier.weight(1f),
                        onClick = if (stats.skipped > 0) onIncludeSkipped else null)
                    Tile("${stats.pendingWithoutGps}", "pendientes sin GPS", AppColors.Muted, Modifier.weight(1f))
                }
                if (stats.skipped > 0) {
                    Text("Toca «para después» para volver a incluirlas en la ruta.", fontSize = 13.sp, color = AppColors.Muted)
                }
                if (stats.deliveredWithoutGps > 0) {
                    Text(
                        "${stats.deliveredWithoutGps} entrega(s) quedaron sin ubicación.",
                        fontSize = 13.sp, color = AppColors.Muted
                    )
                }

                val open = stats.barrios.filter { it.pending > 0 }
                if (open.isNotEmpty()) {
                    var allBarrios by remember { mutableStateOf(false) }
                    Section("BARRIOS CON MÁS PENDIENTES (toca uno para verlo)")
                    (if (allBarrios) open else open.take(8)).forEach { b ->
                        Column(Modifier.fillMaxWidth().clickable { onShowBarrio(b.name) }.padding(vertical = 2.dp)) {
                            Row {
                                Text(b.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("faltan ${b.pending} de ${b.total}", fontSize = 13.sp, color = AppColors.Muted)
                            }
                            LinearProgressIndicator(
                                progress = { b.fraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = AppColors.Delivered,
                                trackColor = AppColors.Pending.copy(alpha = 0.18f)
                            )
                        }
                    }
                    if (open.size > 8) {
                        TextButton(onClick = { allBarrios = !allBarrios }) {
                            Text(if (allBarrios) "Ver menos" else "Ver los ${open.size} barrios (lista completa)", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (stats.total > 0) {
                    Text("🎉 ¡Todos los barrios terminados!", fontWeight = FontWeight.Bold, color = AppColors.Done)
                }
            }
        },
        confirmButton = { Button(onClick = onShare) { Text("Compartir resumen") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cerrar") } }
    )
}
