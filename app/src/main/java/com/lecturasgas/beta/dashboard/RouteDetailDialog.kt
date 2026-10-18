package com.lecturasgas.beta.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.readings.BarrioStat
import com.lecturasgas.beta.readings.Dashboard
import com.lecturasgas.beta.ui.components.ProgressRing
import com.lecturasgas.beta.ui.theme.AppColors

/** Cuántas lecturas/entregas se hicieron en un día (inicio del día en milisegundos). */
class DayCount(val day: Long, val count: Int)

/** Los últimos 7 días (terminando hoy), con 0 en los días sin trabajo. */
fun weekCounts(times: List<Long>, now: Long = System.currentTimeMillis()): List<DayCount> {
    val today = Dashboard.startOfDay(now)
    val byDay = times.groupingBy { Dashboard.startOfDay(it) }.eachCount()
    return (6 downTo 0).map { back ->
        val d = Dashboard.startOfDay(today - back * 24L * 3600_000L + 12L * 3600_000L)
        DayCount(d, byDay[d] ?: 0)
    }
}

private fun shortDay(d: Long): String =
    java.text.SimpleDateFormat("EEE", java.util.Locale("es", "CO")).format(java.util.Date(d))
        .replace(".", "").take(3).replaceFirstChar { it.uppercase() }

private fun longDay(d: Long): String =
    java.text.SimpleDateFormat("EEEE d 'de' MMM", java.util.Locale("es", "CO")).format(java.util.Date(d))

/**
 * Detalle de un anillo de Inicio (Lecturas o Entregas): anillo animado, datos de hoy, gráfica de los
 * últimos 7 días (toca una barra) y avance por barrio (toca un barrio para ir a sus medidores).
 */
@Composable
fun RouteDetailDialog(
    title: String,
    unit: String,
    color: Color,
    done: Int,
    total: Int,
    today: Int,
    perHour: Double?,
    etaMinutes: Long?,
    week: List<DayCount>,
    barrios: List<BarrioStat>,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBarrio: (String) -> Unit,
    onClose: () -> Unit
) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val pending = total - done
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val animated by animateFloatAsState(if (go) fraction else 0f, tween(900), label = "ring")
    val k by animateFloatAsState(if (go) 1f else 0f, tween(1100), label = "count")
    var ringMode by remember { mutableIntStateOf(0) }       // toca el anillo: % → leídas → faltan
    var tab by remember { mutableIntStateOf(0) }            // 0 = días, 1 = barrios
    var picked by remember { mutableIntStateOf(week.lastIndex) }
    var onlyPending by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = AppColors.TopBar, contentColor = Color.White) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onClose) { Text("‹ Cerrar", color = Color.White, fontWeight = FontWeight.Bold) }
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ----- Anillo grande (toca para cambiar lo que dice el centro) -----
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(AppColors.Card, RoundedCornerShape(24.dp))
                                .clickable { ringMode = (ringMode + 1) % 3 }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProgressRing(fraction = animated, color = color, size = 140.dp, strokeWidth = 14.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    when (ringMode) {
                                        0 -> {
                                            Text("${(animated * 100).toInt()}%", fontSize = 32.sp, fontWeight = FontWeight.Black, color = color)
                                            Text("avance", fontSize = 12.sp, color = AppColors.Muted)
                                        }
                                        1 -> {
                                            Text("${(done * k).toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = color)
                                            Text(unit, fontSize = 12.sp, color = AppColors.Muted)
                                        }
                                        else -> {
                                            Text("${(pending * k).toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = AppColors.Pending)
                                            Text("faltan", fontSize = 12.sp, color = AppColors.Muted)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(18.dp))
                            Column {
                                Text("${(done * k).toInt()}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = color)
                                Text("de $total $unit", fontSize = 15.sp)
                                Text(
                                    "Faltan ${(pending * k).toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Pending,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .background(AppColors.Pending.copy(alpha = 0.12f), RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                Text("Toca el anillo", fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    // ----- Datos de hoy -----
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Kpi("$today", "hoy", color, Modifier.weight(1f))
                            Kpi(perHour?.let { "${it.toInt()}/h" } ?: "—", "ritmo", AppColors.Pending, Modifier.weight(1f))
                            Kpi(etaMinutes?.let { Dashboard.formatMinutes(it) } ?: "—", "para terminar", AppColors.Accent, Modifier.weight(1f))
                        }
                    }
                    // ----- Selector -----
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Últimos 7 días") })
                            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Por barrio (${barrios.size})") })
                        }
                    }
                    if (tab == 0) {
                        item {
                            val max = (week.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
                            val sel = week.getOrNull(picked)
                            Column(Modifier.fillMaxWidth().background(AppColors.Card, RoundedCornerShape(20.dp)).padding(16.dp)) {
                                Text(
                                    if (sel == null) "" else "${longDay(sel.day).replaceFirstChar { it.uppercase() }}: ${sel.count} $unit",
                                    fontWeight = FontWeight.Black, fontSize = 15.sp, color = color
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier.fillMaxWidth().height(150.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    week.forEachIndexed { i, d ->
                                        DayBar(
                                            day = d, index = i, selected = i == picked, max = max, color = color,
                                            modifier = Modifier.weight(1f).fillMaxSize(),
                                            onClick = { picked = i }
                                        )
                                    }
                                }
                                val worked = week.count { it.count > 0 }
                                if (worked > 0) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "Promedio: ${week.sumOf { it.count } / worked} por día trabajado (${worked} de 7 días).",
                                        fontSize = 13.sp, color = AppColors.Muted
                                    )
                                } else {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Aún no hay $unit en estos 7 días.", fontSize = 13.sp, color = AppColors.Muted)
                                }
                                // Datos curiosos para animar el trabajo.
                                val best = week.maxByOrNull { it.count }
                                val avg = if (worked > 0) week.sumOf { it.count } / worked else 0
                                val todayCount = week.lastOrNull()?.count ?: 0
                                val insights = buildList {
                                    if (best != null && best.count > 0) add("🏆 Mejor día: ${shortDay(best.day)} con ${best.count}")
                                    if (avg > 0) add(
                                        when {
                                            todayCount > avg -> "▲ Hoy vas ${todayCount - avg} por encima de tu promedio"
                                            todayCount == avg -> "＝ Hoy vas igual a tu promedio"
                                            else -> "▼ Hoy te faltan ${avg - todayCount} para tu promedio"
                                        }
                                    )
                                    if (avg > 0 && pending > 0) add("📅 Al ritmo del promedio terminas en ~${(pending + avg - 1) / avg} días de trabajo")
                                }
                                if (insights.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    insights.forEach { line ->
                                        Text(
                                            line, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                                .background(color.copy(alpha = 0.10f), RoundedCornerShape(50))
                                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = onlyPending, onClick = { onlyPending = true }, label = { Text("Con pendientes") })
                                FilterChip(selected = !onlyPending, onClick = { onlyPending = false }, label = { Text("Todos") })
                            }
                        }
                        val shown = barrios
                            .filter { !onlyPending || it.pending > 0 }
                            .sortedWith(compareByDescending<BarrioStat> { it.pending }.thenBy { it.name })
                        items(shown, key = { it.name }) { b ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(AppColors.Card, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onBarrio(b.name) }
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (b.pending == 0) "Terminado ✓" else "faltan ${b.pending}",
                                        fontWeight = FontWeight.Black, fontSize = 13.sp,
                                        color = if (b.pending == 0) color else AppColors.Pending
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { b.fraction },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = color, trackColor = AppColors.RingTrack
                                )
                                Text("${b.read} de ${b.total}", fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                Surface(color = AppColors.Card, shadowElevation = 8.dp) {
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 22.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
                    ) { Text(primaryLabel, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Kpi(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(AppColors.Card, RoundedCornerShape(18.dp)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
        Text(label, fontSize = 13.sp, color = AppColors.Muted, maxLines = 1)
    }
}

/** Una barra de la gráfica: crece con una animación (cada una con un pequeño retraso). */
@Composable
private fun DayBar(day: DayCount, index: Int, selected: Boolean, max: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val target = (96f * day.count / max).coerceAtLeast(if (day.count > 0) 6f else 3f)
    val h by animateFloatAsState(if (go) target else 0f, tween(650, delayMillis = index * 70), label = "bar")
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text("${day.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) color else AppColors.Muted)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(h.dp).background(
                if (selected) color else color.copy(alpha = 0.35f),
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            shortDay(day.day), fontSize = 12.sp, textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal
        )
    }
}
