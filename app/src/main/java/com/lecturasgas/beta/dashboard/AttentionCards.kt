package com.lecturasgas.beta.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors

/**
 * Colores de «Requiere atención». Cada color tiene un significado fijo:
 *  rojo = error probable · naranja = sospechoso, revisar · ámbar = falta o falla la ubicación ·
 *  turquesa = sin consumo · azul = correcciones tuyas · morado = medidores nuevos ·
 *  marrón = lo que dejaste para después · gris = pendientes.
 */
object AttentionColors {
    val red get() = AppColors.Error
    val orange get() = if (AppColors.dark) Color(0xFFFF9E64) else Color(0xFFD84315)
    val amber get() = AppColors.Warning
    val teal get() = if (AppColors.dark) Color(0xFF4DD0E1) else Color(0xFF00838F)
    val blue get() = AppColors.Brand
    val purple get() = AppColors.Pending
    val brown get() = AppColors.Skipped
    val slate get() = AppColors.Muted
}

/** Tarjeta de atención: icono de color, el número grande, el nombre y una línea que explica qué significa. */
@Composable
internal fun AttentionCard(a: AlertItem, index: Int, modifier: Modifier = Modifier) {
    val pulse by rememberPulse(0.45f, 1f, 1000)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .appear(4 + index, 60L)
            .clip(shape)
            .background(AppColors.Card)
            .background(a.color.copy(alpha = 0.07f))
            .border(1.5.dp, a.color.copy(alpha = if (a.urgent) 0.28f + 0.32f * pulse else 0.32f), shape)
            .clickable(onClick = a.onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(38.dp).background(a.color.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(a.icon, contentDescription = null, tint = a.color, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.count, fontSize = 26.sp, fontWeight = FontWeight.Black, color = a.color, maxLines = 1)
                if (a.urgent) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.background(a.color, CircleShape))
                }
            }
            Text(a.short, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            Spacer(Modifier.height(2.dp))
            Text(a.hint, fontSize = 12.sp, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        }
    }
}
