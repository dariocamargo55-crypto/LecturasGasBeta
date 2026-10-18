package com.lecturasgas.beta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.data.model.DeliverySession
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.Geo
import com.lecturasgas.beta.util.TimeUtil
import kotlinx.coroutines.delay

/**
 * Tarjeta de arriba, igual en Lecturas y en Entrega: anillo grande con el avance, datos del día y
 * botones circulares (▶ iniciar / ■ terminar el recorrido, y opcionalmente el de paquete).
 */
@Composable
fun RouteTopCard(
    fraction: Float,
    percent: Int,
    color: Color,
    done: Int,
    total: Int,
    pending: Int,
    extraPill: String?,
    extraColor: Color,
    detail: String,
    session: DeliverySession?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onOpenSummary: () -> Unit,
    onPackage: (() -> Unit)? = null,
    onOrder: (() -> Unit)? = null
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.id) {
        while (session != null) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable(onClick = onOpenSummary),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressRing(fraction = fraction, color = color, size = 84.dp, strokeWidth = 8.dp) {
                        Text("$percent%", fontSize = 19.sp, fontWeight = FontWeight.Black, color = color)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$done", fontSize = 32.sp, fontWeight = FontWeight.Black, color = color)
                            Text(" de $total", fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("Faltan $pending", AppColors.Pending)
                            if (extraPill != null) StatusPill(extraPill, extraColor)
                        }
                        Text(detail, fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircleAction(
                        icon = if (session == null) AppIcons.Play else AppIcons.Stop,
                        color = if (session == null) AppColors.Done else AppColors.Error,
                        description = if (session == null) "Iniciar recorrido" else "Terminar recorrido",
                        onClick = if (session == null) onStart else onEnd
                    )
                    if (onOrder != null) {
                        CircleAction(
                            icon = AppIcons.ListLines,
                            color = AppColors.Brand,
                            description = "Orden de lectura",
                            onClick = onOrder
                        )
                    }
                    if (onPackage != null) {
                        CircleAction(
                            icon = AppIcons.Box,
                            color = AppColors.Next,
                            description = "Entregar por paquete (buzón general)",
                            onClick = onPackage
                        )
                    }
                }
            }
            if (session != null) {
                Text(
                    "● Grabando · ${Geo.formatDistance(session.distanceMeters)} · ${TimeUtil.duration(now - session.startedAt)}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Done,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun CircleAction(icon: ImageVector, color: Color, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        contentColor = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier.size(50.dp)
    ) {
        Box(Modifier.fillMaxSize().semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}
