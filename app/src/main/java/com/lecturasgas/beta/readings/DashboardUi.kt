package com.lecturasgas.beta.readings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.ui.components.ProgressRing
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.TimeUtil

/**
 * Tarjeta de avance: anillo con el porcentaje, lo que llevas y lo que falta.
 * Se usa en Lecturas y en Entrega. Al tocarla se abre el resumen completo.
 */
@Composable
fun ProgressCard(
    done: Int,
    total: Int,
    doneLabel: String,
    color: Color,
    pending: Int,
    detail: String?,
    extra: String?,
    extraColor: Color,
    compact: Boolean,
    onClick: () -> Unit
) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressRing(
                fraction = fraction,
                color = color,
                size = if (compact) 48.dp else 78.dp,
                strokeWidth = if (compact) 5.dp else 7.dp
            ) {
                Text("${(fraction * 100).toInt()}%", fontSize = if (compact) 13.sp else 18.sp, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$done", fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.Black, color = color)
                    Text(" de $total $doneLabel", fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Faltan $pending",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Pending,
                        modifier = Modifier
                            .background(AppColors.Pending.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                    if (extra != null && !compact) {
                        Text(
                            extra, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = extraColor,
                            modifier = Modifier
                                .background(extraColor.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                if (!compact && detail != null) {
                    Text(
                        detail, fontSize = 13.sp, color = AppColors.Muted, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Resumen", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Icon(AppIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Cuatro mosaicos de resumen (Leídas / Pendientes / Con motivo / Sin GPS); los de arriba filtran la lista. */
@Composable
fun StatTiles(
    read: Int,
    pending: Int,
    skipped: Int,
    withoutGps: Int,
    onRead: () -> Unit,
    onPending: () -> Unit,
    onSkipped: () -> Unit,
    onGps: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile("$read", "Leídas", AppColors.Done, Modifier.weight(1f), onRead)
        StatTile("$pending", "Pendientes", AppColors.Pending, Modifier.weight(1f), onPending)
        StatTile("$skipped", "Con motivo", AppColors.Skipped, Modifier.weight(1f), onSkipped)
        StatTile("$withoutGps", "Sin GPS", AppColors.Warning, Modifier.weight(1f), onGps)
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Encabezado de la pantalla Lecturas. */
@Composable
fun DashboardHeader(
    read: Int,
    total: Int,
    pending: Int,
    skipped: Int,
    withoutGps: Int,
    /** Versión mínima: se usa con el teclado abierto para dejar espacio. */
    compact: Boolean = false,
    onClick: () -> Unit
) {
    ProgressCard(
        done = read, total = total, doneLabel = "leídas", color = AppColors.Done,
        pending = pending,
        detail = if (withoutGps > 0) "$withoutGps sin GPS · toca para ver el resumen" else "Toca para ver el resumen",
        extra = if (skipped > 0) "$skipped con motivo" else null,
        extraColor = AppColors.Skipped,
        compact = compact, onClick = onClick
    )
}

/**
 * Resumen completo: avance, ritmo de hoy, estado de la ruta y barrios con más pendientes.
 * Cada dato se puede tocar para ir directo a esos medidores.
 */
@Composable
fun DashboardDialog(
    routeName: String,
    stats: DashboardStats,
    onShowPending: () -> Unit,
    onShowSkipped: () -> Unit,
    onShowBarrio: (String) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    suspects: Int = 0,
    onReviewSuspects: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(routeName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ---------- Avance ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(fraction = stats.fraction, color = AppColors.Done, size = 92.dp, strokeWidth = 8.dp) {
                        Text("${stats.percent}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppColors.Done)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${stats.read}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = AppColors.Done)
                        Text("de ${stats.total} leídas", fontSize = 15.sp)
                    }
                }

                // ---------- Hoy ----------
                Section("HOY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("${stats.readToday}", "leídas hoy", AppColors.Done, Modifier.weight(1f))
                    Tile(
                        stats.perHour?.let { "${it.toInt()}/h" } ?: "—",
                        "ritmo", AppColors.Pending, Modifier.weight(1f)
                    )
                    Tile(
                        stats.etaMinutes?.let { Dashboard.formatMinutes(it) } ?: "—",
                        "para terminar", AppColors.Accent, Modifier.weight(1f)
                    )
                }
                if (stats.perHour == null) {
                    Text(
                        "El ritmo y el tiempo estimado aparecen cuando llevas unas 5 lecturas en 15 minutos.",
                        fontSize = 13.sp, color = AppColors.Muted
                    )
                } else if (stats.firstTodayAt != null) {
                    Text("Empezaste a las ${TimeUtil.hour(stats.firstTodayAt)}.", fontSize = 13.sp, color = AppColors.Muted)
                }

                // ---------- Estado ----------
                Section("ESTADO DE LA RUTA")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("${stats.pending}", "pendientes", AppColors.Pending, Modifier.weight(1f), onClick = onShowPending)
                    Tile("${stats.skipped}", "con motivo", AppColors.Skipped, Modifier.weight(1f),
                        onClick = if (stats.skipped > 0) onShowSkipped else null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("${stats.withoutGps}", "sin GPS", AppColors.Muted, Modifier.weight(1f))
                    Tile("${stats.meterFixes}", "medidores corregidos", AppColors.Warning, Modifier.weight(1f))
                }
                if (stats.newPending > 0) {
                    Tile("${stats.newPending}", "medidores NUEVOS sin leer", AppColors.Accent, Modifier.fillMaxWidth())
                }
                if (suspects > 0) {
                    Tile("$suspects", "ubicaciones sospechosas (toca para revisar)", AppColors.Warning, Modifier.fillMaxWidth(), onClick = onReviewSuspects)
                }
                if (stats.negatives > 0) {
                    Text(
                        "⚠ ${stats.negatives} lectura(s) menores que la anterior. Conviene revisarlas.",
                        fontSize = 13.sp, color = AppColors.Error, fontWeight = FontWeight.Bold
                    )
                }

                // ---------- Barrios ----------
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
                                color = AppColors.Done,
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

@Composable
internal fun Section(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Black, color = AppColors.Muted)
}

@Composable
internal fun Tile(value: String, label: String, color: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
            Text(label, fontSize = 12.sp, color = AppColors.Muted, maxLines = 2)
        }
    }
}
