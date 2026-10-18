package com.lecturasgas.beta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.location.Near
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.Geo

/**
 * "Estás frente a NIR 104" con el botón para leerlo, y/o "Mejorar ubicación" cuando el GPS
 * guardado de ese medidor es dudoso. Vibra una vez cuando aparece un medidor nuevo.
 */
@Composable
fun ProximityBanner(
    near: Near,
    improving: Boolean,
    onRead: () -> Unit,
    onImprove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    vibrate: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(near.record.row) {
        if (vibrate) runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    val r = near.record
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.TintViolet,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (near.unread) "Estás frente a " else "Estás cerca de ") +
                                "NIR ${r.nir} · ${Geo.formatDistance(near.meters)}",
                        fontSize = 13.sp, color = AppColors.Muted
                    )
                    Text(
                        r.user.ifBlank { r.displayMeter },
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        r.displayMeter + (if (r.address.isNotBlank()) " · ${r.address}" else ""),
                        fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (near.unread) {
                    Button(
                        onClick = onRead,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("LEER", fontWeight = FontWeight.Black) }
                }
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("✕", fontSize = 16.sp, color = AppColors.Muted)
                }
            }
            if (near.weak) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.locationAccuracy?.let { "Su ubicación es dudosa (±${it.toInt()} m)" } ?: "Su ubicación es dudosa",
                        fontSize = 13.sp, color = AppColors.Warning, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onImprove,
                        enabled = near.canImprove && !improving,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            when {
                                improving -> "Midiendo…"
                                near.canImprove -> "Mejorar ubicación"
                                else -> "Esperando mejor señal…"
                            },
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
