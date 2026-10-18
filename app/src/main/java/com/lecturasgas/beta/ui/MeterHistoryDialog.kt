package com.lecturasgas.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.review.MeterHistory
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.TimeUtil

/**
 * Historial de un medidor: cómo va este mes frente a los anteriores, las lecturas guardadas de meses pasados
 * y los cambios que le hiciste a la lectura de este mes.
 */
@Composable
fun MeterHistoryDialog(record: MeterRecord, history: MeterHistory.View, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text("Historial del medidor", fontWeight = FontWeight.Black)
                Text("NIR ${record.nir} · ${record.displayMeter}", fontSize = 14.sp, color = AppColors.Muted)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (history.average != null) {
                    Column(Modifier.fillMaxWidth().background(AppColors.Brand.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Text("Consumo promedio de meses anteriores: ${history.average}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        history.comparison?.let {
                            Text("Este mes va $it frente a ese promedio.", fontSize = 13.sp, color = AppColors.Muted)
                        }
                    }
                }
                history.entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().background(AppColors.CardAlt, RoundedCornerShape(12.dp)).padding(10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(e.note, fontSize = 12.sp, color = AppColors.Muted)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(e.reading?.toString() ?: "—", fontWeight = FontWeight.Black, fontSize = 18.sp)
                            e.consumption?.let {
                                Text(
                                    "consumo " + (if (it >= 0) "+" else "") + it, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (it < 0) AppColors.Error else AppColors.Done
                                )
                            }
                        }
                    }
                }
                if (history.entries.size <= 2) {
                    Text(
                        "Los meses anteriores se van guardando solos: cada vez que importas el Excel de un mes nuevo, la lectura de este mes pasa al historial.",
                        fontSize = 12.sp, color = AppColors.Muted
                    )
                }
                if (history.changes.isNotEmpty()) {
                    Text("Cambios en la app", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    history.changes.forEach { c ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(TimeUtil.dateTime(c.at), fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.weight(1f))
                            Text(
                                when (c.kind) {
                                    "lectura" -> "Lectura ${c.value}"
                                    "corrección" -> "Corregida a ${c.value}"
                                    "borrada" -> "Borrada"
                                    else -> "${c.kind} ${c.value ?: ""}"
                                },
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (c.kind == "borrada") AppColors.Error else AppColors.Muted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar", fontWeight = FontWeight.Bold) } }
    )
}
