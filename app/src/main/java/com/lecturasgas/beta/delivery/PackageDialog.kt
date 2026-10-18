package com.lecturasgas.beta.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors

private fun barrioOf(r: MeterRecord): String = r.barrio.trim().uppercase().ifBlank { "SIN BARRIO" }

/**
 * Entrega por paquete: armas el paquete sumando facturas una a una (buscando por medidor, NIR,
 * nombre o dirección) y, al final, las registras todas de una vez como entregadas.
 * El chip de barrio solo filtra lo que ves; lo marcado se conserva al cambiar de barrio.
 */
@Composable
fun PackageDialog(
    pending: List<MeterRecord>,
    suggestions: List<String>,
    onConfirm: (rows: List<Int>, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf(listOf<Int>()) }   // en el orden en que las marcaste
    var query by remember { mutableStateOf("") }
    var barrio by remember { mutableStateOf<String?>(null) }    // null = todos
    var note by remember { mutableStateOf("Buzón general") }
    var reviewing by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val byRow = remember(pending) { pending.associateBy { it.row } }
    val barrios = remember(pending) {
        pending.groupBy { barrioOf(it) }.map { it.key to it.value.size }.sortedBy { it.first }
    }
    val pickedSet = picked.toHashSet()
    val shown = remember(pending, query, barrio) {
        val q = query.trim().lowercase()
        pending.asSequence()
            .filter { barrio == null || barrioOf(it) == barrio }
            .filter {
                q.isEmpty() || (it.address + " " + it.user + " " + it.nir + " " + it.meter + " " + it.displayMeter + " " + it.barrio)
                    .lowercase().contains(q)
            }
            .sortedBy { it.nirNumber ?: Long.MAX_VALUE }
            .take(300)
            .toList()
    }
    val close: () -> Unit = { if (picked.isEmpty()) onDismiss() else confirmClose = true }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Surface(color = AppColors.TopBar, contentColor = Color.White) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { if (reviewing) reviewing = false else close() }) {
                            Text(if (reviewing) "‹ Volver" else "‹ Cerrar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Icon(AppIcons.Box, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (reviewing) "Tu paquete" else "Entregar por paquete",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (reviewing) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Toca una factura para sacarla del paquete.",
                            fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(12.dp)
                        )
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(picked, key = { it }) { row ->
                                val r = byRow[row]
                                if (r != null) PackageCard(r, marked = true, onClick = { picked = picked - row })
                            }
                        }
                    }
                } else {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Buscar por medidor, NIR, nombre o dirección…") },
                            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                FilterChip(
                                    selected = barrio != null,
                                    onClick = { menuOpen = true },
                                    label = { Text("Barrio: ${barrio ?: "Todos"} ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Todos (${pending.size})") },
                                        onClick = { barrio = null; menuOpen = false }
                                    )
                                    barrios.forEach { (name, n) ->
                                        DropdownMenuItem(
                                            text = { Text("$name ($n)") },
                                            onClick = { barrio = name; menuOpen = false }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            if (shown.isNotEmpty()) {
                                TextButton(onClick = {
                                    val add = shown.map { it.row }.filter { it !in pickedSet }
                                    picked = picked + add
                                }) { Text("Marcar las ${shown.size} que se ven", fontSize = 13.sp) }
                            }
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    if (pending.isEmpty()) "No quedan facturas por entregar." else "Sin resultados. Prueba con otro texto o cambia el barrio.",
                                    color = AppColors.Muted, modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        items(shown, key = { it.row }) { r ->
                            val on = r.row in pickedSet
                            PackageCard(r, marked = on, onClick = { picked = if (on) picked - r.row else picked + r.row })
                        }
                    }
                }

                // Barra fija de abajo: el paquete que vas armando.
                Surface(color = AppColors.Card, shadowElevation = 8.dp) {
                    Column(Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Paquete: ${picked.size} ${if (picked.size == 1) "factura" else "facturas"}",
                                fontWeight = FontWeight.Black, fontSize = 16.sp, color = AppColors.Delivered,
                                modifier = Modifier.weight(1f)
                            )
                            if (picked.isNotEmpty()) {
                                TextButton(onClick = { reviewing = !reviewing }) { Text(if (reviewing) "Seguir buscando" else "Ver lista") }
                                TextButton(onClick = { picked = emptyList(); reviewing = false }) { Text("Vaciar", color = AppColors.Error) }
                            }
                        }
                        OutlinedTextField(
                            value = note, onValueChange = { note = it.take(80) }, singleLine = true,
                            label = { Text("Dónde se dejó") }, modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onConfirm(picked, note) },
                            enabled = picked.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Delivered, contentColor = Color.White)
                        ) {
                            Text(
                                if (picked.isEmpty()) "MARCA FACTURAS PARA ARMAR EL PAQUETE" else "ENTREGAR ${picked.size} FACTURAS",
                                fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("¿Descartar el paquete?") },
            text = { Text("Llevas ${picked.size} facturas marcadas. Si sales, se pierde la selección (no se entrega nada).") },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; onDismiss() }) { Text("Descartar", color = AppColors.Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Seguir armando") } }
        )
    }
}

@Composable
private fun PackageCard(r: MeterRecord, marked: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = if (marked) AppColors.TintGreen else AppColors.Card
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "NIR ${r.nir.ifBlank { "—" }} · ${r.displayMeter}",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(r.user.ifBlank { "Sin nombre" }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    r.address + " · " + barrioOf(r),
                    fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (marked) "✔" else "＋",
                fontSize = 22.sp, fontWeight = FontWeight.Black,
                color = if (marked) AppColors.Delivered else AppColors.Muted
            )
        }
    }
}
