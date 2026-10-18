package com.lecturasgas.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.review.ExportReview
import com.lecturasgas.beta.review.ExportReview.Kind
import com.lecturasgas.beta.review.ExportReview.Level
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors

private val RED = Color(0xFFC62828)
private val AMBER = Color(0xFFB26A00)
private val GREEN = Color(0xFF1E8E3E)

private fun levelColor(level: Level): Color = when (level) {
    Level.ERROR -> RED
    Level.WARNING -> AMBER
    Level.INFO -> Color(0xFF0B4F8A)
}

/**
 * Pantalla «Revisión antes de exportar»: junta lo raro (lecturas menores a la anterior, consumos exagerados,
 * lecturas del Excel que se van a cambiar, medidores sin leer…). Tocar un medidor abre su tarjeta para corregirlo;
 * la lista se actualiza sola. Desde aquí también se puede «Comprobar» el Excel sin guardarlo, o exportarlo.
 */
@Composable
fun ExportReviewDialog(
    review: ExportReview.Result,
    routeName: String,
    onOpenMeter: (Int) -> Unit,
    onCheck: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit
) {
    val headColor = when {
        review.hasErrors -> RED
        review.hasWarnings -> AMBER
        else -> GREEN
    }
    // Errores y avisos abiertos; lo informativo, cerrado.
    val open = remember { mutableStateMapOf<Kind, Boolean>() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = headColor, contentColor = Color.White) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 14.dp)) {
                        TextButton(onClick = onClose) { Text("‹ Volver", color = Color.White, fontWeight = FontWeight.Bold) }
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Icon(if (review.clean) AppIcons.Check else AppIcons.Warning, contentDescription = null, modifier = Modifier.size(24.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Revisión antes de exportar", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Text(
                                    when {
                                        review.hasErrors -> "${review.errorCount} lecturas conviene corregirlas antes de exportar"
                                        review.hasWarnings -> "${review.warningCount} cosas para mirar dos veces"
                                        else -> "Todo en orden ✓"
                                    },
                                    fontSize = 14.sp, color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Summary(review, routeName) }
                    review.sections.forEach { section ->
                        item(key = section.kind.name) {
                            val expanded = open[section.kind] ?: (section.kind.level != Level.INFO)
                            SectionCard(section, expanded, { open[section.kind] = !expanded }, onOpenMeter)
                        }
                    }
                    item {
                        Text(
                            "Esto solo mira lo que la app va a escribir. Las demás celdas del Excel no se tocan: el archivo sale " +
                                    "igual al original y únicamente se llena la columna de lectura de los medidores que leíste.",
                            fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Surface(color = AppColors.Card, shadowElevation = 8.dp) {
                    Row(
                        // Dentro de un Dialog el inset de la barra de navegación puede llegar en 0: se exige un mínimo
                        // de 28 dp abajo para que los botones nunca queden bajo la barra de gestos.
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = 28.dp)))
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onCheck, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Comprobar", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onExport,
                            modifier = Modifier.weight(1.4f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (review.hasErrors) RED else GREEN, contentColor = Color.White)
                        ) {
                            Text(if (review.hasErrors) "Exportar igual" else "Exportar", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(review: ExportReview.Result, routeName: String) {
    Column(
        Modifier.fillMaxWidth().background(AppColors.Card, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(routeName, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${review.toWrite} lecturas tomadas con la app se escribirán en la columna de lectura del Excel.", fontSize = 14.sp)
        val unread = review.count(Kind.UNREAD) + review.count(Kind.SKIPPED)
        if (unread > 0) Text("$unread medidores quedarán sin lectura (vacíos en el Excel).", fontSize = 14.sp, color = AppColors.Muted)
        review.typicalConsumption?.let { Text("Consumo típico de esta ruta: ≈ $it", fontSize = 13.sp, color = AppColors.Muted) }
    }
}

@Composable
private fun SectionCard(
    section: ExportReview.Section,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenMeter: (Int) -> Unit
) {
    val color = levelColor(section.kind.level)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Card, RoundedCornerShape(18.dp))
            .border(1.5.dp, color.copy(alpha = if (section.kind.level == Level.INFO) 0.12f else 0.35f), RoundedCornerShape(18.dp))
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(section.kind.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                "${section.items.size}", fontWeight = FontWeight.Black, color = color, fontSize = 14.sp,
                modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "⌃" else "⌄", color = AppColors.Muted, fontSize = 16.sp)
        }
        if (expanded) {
            Text(section.kind.hint, fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp))
            val shown = section.items.take(MAX_SHOWN)
            shown.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenMeter(item.row) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("NIR ${item.nir} · ${item.meter}", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.user.isNotBlank()) Text(item.user, fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.detail.isNotBlank()) Text(item.detail, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                    Icon(AppIcons.Chevron, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                }
            }
            if (section.items.size > MAX_SHOWN) {
                Text(
                    "… y ${section.items.size - MAX_SHOWN} más",
                    fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                )
            } else Spacer(Modifier.height(6.dp))
        }
    }
}

private const val MAX_SHOWN = 40
