package com.lecturasgas.beta.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lecturasgas.beta.dashboard.EntryAction
import com.lecturasgas.beta.dashboard.ListDialog
import com.lecturasgas.beta.dashboard.ListEntry
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.location.Suspect
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.Geo

/**
 * Revisar los medidores cuyo punto GPS parece estar lejos de donde de verdad están.
 * Usa la misma lista a pantalla completa que las demás tarjetas de «Requiere atención».
 */
@Composable
fun SuspectsDialog(
    suspects: List<Suspect>,
    fix: GeoFix? = null,
    onShowOnMap: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit
) {
    // Ordenados por cercanía a ti (se reordena solo cuando te mueves unas decenas de metros).
    val keyLat = fix?.let { (it.lat * 2000).toInt() }
    val keyLon = fix?.let { (it.lon * 2000).toInt() }
    val ordered = remember(suspects, keyLat, keyLon) {
        if (fix == null) suspects
        else suspects.sortedBy { Geo.distanceMeters(fix.lat, fix.lon, it.record.lat ?: fix.lat, it.record.lon ?: fix.lon) }
    }
    var confirmAll by remember { mutableStateOf(false) }

    val entries = ordered.map { s ->
        val r = s.record
        val nearYou = if (fix != null && r.lat != null && r.lon != null)
            " · a ${Geo.formatDistance(Geo.distanceMeters(fix.lat, fix.lon, r.lat, r.lon))} de ti" else ""
        ListEntry(
            title = "NIR ${r.nir} · ${r.user.ifBlank { r.displayMeter }}",
            subtitle = (r.barrio.takeIf { it.isNotBlank() } ?: r.address) + nearYou,
            extra = "A ${Geo.formatDistance(s.meters)} de ${s.reference}",
            actions = listOf(EntryAction("Borrar punto", danger = true) { onClear(r.row) })
        ) { onShowOnMap(r.row) }
    }

    ListDialog(
        title = if (suspects.isEmpty()) "Ubicaciones" else "Ubicaciones sospechosas",
        hint = "Aparecen lejos de su barrio o de sus vecinos. Toca uno para verlo en el mapa; si borras el punto, " +
            "queda «sin GPS» y se mide de nuevo al leer o entregar." +
            (if (fix != null) " Del más cercano a ti al más lejano." else ""),
        entries = entries,
        color = AppColors.Warning,
        icon = AppIcons.Warning,
        emptyText = "✅ No hay puntos lejos de su barrio ni de sus vecinos. Todo se ve bien.",
        headerAction = EntryAction("Borrar todos") { confirmAll = true },
        onClose = onClose
    )

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("¿Borrar los ${suspects.size} puntos?") },
            text = { Text("Esos medidores quedarán sin GPS y se medirán de nuevo la próxima vez que los leas o entregues. Las lecturas no cambian.") },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; onClearAll() }) {
                    Text("Borrar todos", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancelar") } }
        )
    }
}
