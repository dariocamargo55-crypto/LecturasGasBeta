package com.lecturasgas.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.review.ReadingOrder
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.TimeUtil
import java.util.Locale

/**
 * «Orden de lectura»: las lecturas tomadas con la app en el orden en que se fueron tomando (1ª, 2ª, 3ª…),
 * agrupadas por día. Es la base para armar, mes a mes, la ruta en el orden en que realmente se lee.
 */
@Composable
fun ReadingOrderDialog(
    records: List<MeterRecord>,
    routeName: String,
    onOpenMeter: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val entries = remember(records) { ReadingOrder.build(records) }
    val days = remember(entries) { ReadingOrder.byDay(entries) }
    val unread = remember(records) { records.count { it.reading == null } }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = AppColors.TopBar, contentColor = Color.White) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 16.dp)) {
                        TextButton(onClick = onClose) { Text("‹ Cerrar", color = Color.White, fontWeight = FontWeight.Bold) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Orden de lectura", fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text(
                                "En qué orden fuiste tomando las lecturas · $routeName",
                                fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (entries.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Kpi("${entries.size}", "lecturas", Modifier.weight(1f))
                                Kpi("${days.size}", if (days.size == 1) "día" else "días", Modifier.weight(1f))
                                Kpi("$unread", "faltan", Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (entries.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.size(84.dp).background(AppColors.Brand.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Eye, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(42.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Todavía no hay lecturas", fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A medida que tomes lecturas, aquí aparecen numeradas en el orden en que las tomaste.",
                            fontSize = 14.sp, color = AppColors.Muted, textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
                    ) {
                        for ((dayStart, list) in days) {
                            item(key = "d_${list.first().seq}") {
                                val first = list.first().at
                                val last = list.last().at
                                Column(Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp)) {
                                    Text(
                                        (dayStart?.let { TimeUtil.dateLong(it) } ?: "Sin fecha").uppercase(Locale.ROOT),
                                        fontSize = 13.sp, fontWeight = FontWeight.Black, color = AppColors.Muted, letterSpacing = 1.sp
                                    )
                                    Text(
                                        "${list.size} lecturas" +
                                            if (dayStart != null) " · de ${TimeUtil.hour(first)} a ${TimeUtil.hour(last)}" else "",
                                        fontSize = 13.sp, color = AppColors.Muted
                                    )
                                }
                            }
                            items(list.size, key = { "e_${list[it].record.row}" }) { i ->
                                OrderRow(list[i], isLast = i == list.lastIndex, onClick = { onOpenMeter(list[i].record.row) })
                            }
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
                            enabled = entries.isNotEmpty(),
                            onClick = onSave,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar como documento de texto", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(e: ReadingOrder.Entry, isLast: Boolean, onClick: () -> Unit) {
    val r = e.record
    val cons = r.previous?.let { r.appReading!! - it }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(34.dp).background(AppColors.Brand, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    "${e.seq}", color = Color.White, fontWeight = FontWeight.Black, maxLines = 1,
                    fontSize = when { e.seq >= 1000 -> 10.sp; e.seq >= 100 -> 12.sp; else -> 14.sp }
                )
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(AppColors.Brand.copy(alpha = 0.25f)))
        }
        Column(
            Modifier.weight(1f).padding(bottom = 10.dp)
                .background(AppColors.Card, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NIR ${r.nir} · ${r.displayMeter}", fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (e.at != Long.MAX_VALUE) Text(TimeUtil.hour(e.at), fontSize = 13.sp, color = AppColors.Muted)
            }
            if (r.user.isNotBlank()) Text(r.user, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val place = listOf(r.address, r.barrio).filter { it.isNotBlank() }.joinToString(" · ")
            if (place.isNotBlank()) Text(place, fontSize = 13.sp, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.background(AppColors.Brand.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lectura ${r.appReading}" + (cons?.let { " · " + if (it > 0) "+$it" else "$it" } ?: ""),
                    fontSize = 12.sp, color = AppColors.Brand, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
