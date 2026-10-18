package com.lecturasgas.beta.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.LaunchedEffect
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors

/**
 * Buscador del mapa: escribes medidor, NIR, usuario, dirección o barrio y, al tocar un
 * resultado, el mapa se acerca a ese medidor y abre su tarjeta.
 */
@Composable
fun MapSearchDialog(
    records: List<MeterRecord>,
    onPick: (MeterRecord) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = text.trim().lowercase()
    val results = remember(q, records) {
        if (q.length < 2) emptyList()
        else records.filter {
            it.hasLocation && (
                it.displayMeter.lowercase().contains(q) || it.meter.lowercase().contains(q) ||
                    it.nir.lowercase().contains(q) || it.user.lowercase().contains(q) ||
                    it.address.lowercase().contains(q) || it.barrio.lowercase().contains(q)
                )
        }.take(40)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = AppColors.Card, shadowElevation = 8.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar en el mapa", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Medidor, NIR, usuario, dirección o barrio") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                Spacer(Modifier.size(8.dp))
                if (q.length < 2) {
                    Text("Escribe al menos 2 letras o números.", fontSize = 13.sp, color = AppColors.Muted)
                } else if (results.isEmpty()) {
                    Text("Sin resultados con ubicación GPS.", fontSize = 13.sp, color = AppColors.Muted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.row }) { r ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { onPick(r) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("NIR ${r.nir} · ${r.displayMeter}", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.user, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(r.address, r.barrio).filter { it.isNotBlank() }.joinToString(" · "),
                                    fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
