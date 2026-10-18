package com.lecturasgas.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.lecturasgas.beta.data.excel.ImportValidator
import com.lecturasgas.beta.util.TimeUtil
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.data.model.RouteSummary
import com.lecturasgas.beta.ui.theme.AppColors

/**
 * Antes de guardar un Excel, confirma a qué ruta pertenece (RUTA A, RUTA B…) y muestra qué va a
 * pasar: si es la misma ruta de otro mes se compara medidor por medidor; si es otra ruta, se guarda
 * aparte y la anterior queda intacta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportRouteDialog(
    state: MainViewModel.ImportState,
    onKeyChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val p = state.preview
    val plan = state.plan
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("¿De qué ruta es este Excel?") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(p.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.total} medidores · ${p.barrios} barrios" + (p.period.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    fontSize = 13.sp, color = AppColors.Muted
                )
                p.check?.let { ExcelCheckSection(it) }
                OutlinedTextField(
                    value = state.key,
                    onValueChange = { onKeyChange(it.take(30)) },
                    label = { Text("Ruta (ej.: RUTA A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (p.routes.isNotEmpty()) {
                    Text("Rutas que ya tienes (toca para elegir):", fontSize = 13.sp, color = AppColors.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.routes.forEach { r ->
                            AssistChip(onClick = { onKeyChange(r.routeKey) }, label = { Text(r.routeKey) })
                        }
                    }
                }
                when {
                    plan == null -> Text("Escribe el nombre de la ruta.", fontSize = 13.sp, color = AppColors.Muted)
                    plan.updates != null -> {
                        Box(
                            "Actualiza «${plan.updates.name}»",
                            if (plan.samePeriod) "Es el mismo mes: se conservan tus lecturas, entregas y todos los puntos GPS."
                            else "Es otro mes: se compara medidor por medidor y se conservan los puntos GPS. " +
                                    "Las lecturas de la app del mes anterior no pasan al archivo nuevo: exporta antes si no lo hiciste."
                        )
                        Text("Frente al Excel anterior:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("• ${plan.newMeters} medidores nuevos", fontSize = 13.sp)
                        Text("• ${plan.renumbered} cambiaron de NIR", fontSize = 13.sp)
                        Text("• ${plan.removed} ya no aparecen", fontSize = 13.sp)
                        if (plan.newBarrios.isNotEmpty()) {
                            Text("• Barrios nuevos: " + plan.newBarrios.take(6).joinToString(", "), fontSize = 13.sp)
                        }
                    }
                    else -> Box(
                        "Ruta nueva para la app",
                        "Se guarda aparte. Tu ruta actual queda guardada tal como está y puedes volver a ella desde el menú ▸ Rutas."
                    )
                }
                if (plan?.blockedBySession == true) {
                    Text(
                        "Tienes una jornada de entrega activa. Termínala (Entrega ▸ Terminar) antes de importar otra ruta u otro mes.",
                        fontSize = 13.sp, color = AppColors.Error, fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = plan != null && !plan.blockedBySession && state.key.isNotBlank()
            ) {
                val risky = p.check?.hasErrors == true
                Text(if (risky) "IMPORTAR IGUAL" else "IMPORTAR", fontWeight = FontWeight.Black, color = if (risky) AppColors.Error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

@Composable
private fun Box(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Brand.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(body, fontSize = 13.sp, color = AppColors.Muted)
    }
}

/** Resultado de revisar el Excel recién leído (encabezados, repetidos, fórmulas…) y de la prueba de exportación. */
@Composable
private fun ExcelCheckSection(check: ImportValidator.Report) {
    var showInfo by remember { mutableStateOf(false) }
    val color = when {
        check.hasErrors -> AppColors.Error
        check.warnings.isNotEmpty() -> AppColors.Warning
        else -> AppColors.Done
    }
    Column(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            when {
                check.hasErrors -> "Revisión del Excel: hay problemas"
                check.warnings.isNotEmpty() -> "Revisión del Excel: ${check.warnings.size} avisos"
                else -> "Revisión del Excel: todo en orden ✓"
            },
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color
        )
        if (check.selfTest.ran && check.selfTest.ok) {
            Text("✔ Prueba de exportación: " + check.selfTest.message, fontSize = 13.sp, color = AppColors.Muted)
        }
        (check.errors + check.warnings).forEach { IssueRow(it) }
        if (check.infos.isNotEmpty()) {
            Text(
                if (showInfo) "Ocultar detalles ⌃" else "Ver más datos del archivo (${check.infos.size}) ⌄",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showInfo = !showInfo }.padding(vertical = 2.dp)
            )
            if (showInfo) check.infos.forEach { IssueRow(it) }
        }
    }
}

@Composable
private fun IssueRow(issue: ImportValidator.Issue) {
    val c = when (issue.level) {
        ImportValidator.Level.ERROR -> AppColors.Error
        ImportValidator.Level.WARNING -> AppColors.Warning
        ImportValidator.Level.INFO -> AppColors.Muted
    }
    Row(verticalAlignment = Alignment.Top) {
        androidx.compose.foundation.layout.Box(Modifier.padding(top = 6.dp).size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(issue.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (issue.detail.isNotBlank()) Text(issue.detail, fontSize = 12.sp, color = AppColors.Muted)
            if (issue.rows.isNotEmpty()) Text("Filas del Excel: " + issue.rows.take(8).joinToString(", ") + if (issue.rows.size > 8) "…" else "", fontSize = 12.sp, color = AppColors.Muted)
        }
    }
}

/** Las rutas guardadas (A, B, C…): cambia de una a otra o borra las que ya no uses. */
@Composable
fun RoutesDialog(
    routes: List<RouteSummary>,
    onSwitch: (Long) -> Unit,
    onImport: () -> Unit,
    onDelete: (routeId: Long, keepHistory: Boolean) -> Unit,
    onClose: () -> Unit
) {
    var deleting by remember { mutableStateOf<RouteSummary?>(null) }
    var keepHistory by remember { mutableStateOf(true) }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar esta ruta?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.name, fontWeight = FontWeight.Bold)
                    Text(
                        "Se borran sus ${r.total} medidores, ${r.appReadings} lecturas hechas con la app y ${r.deliveries} entregas con sus recorridos.",
                        fontSize = 14.sp
                    )
                    if (r.unexported > 0) {
                        Text(
                            "⚠ ${r.unexported} lecturas todavía NO están en un Excel exportado. Si borras la ruta se pierden. Exporta antes.",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Error
                        )
                    }
                    Text("Los puntos GPS de los medidores se conservan.", fontSize = 14.sp, color = AppColors.Muted)
                    Row(
                        Modifier.fillMaxWidth().clickable { keepHistory = !keepHistory },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = keepHistory, onCheckedChange = { keepHistory = it })
                        Text("Conservar el historial de lecturas de cada medidor", fontSize = 14.sp)
                    }
                    if (r.active) {
                        Text(
                            if (routes.size > 1) "Es la ruta en uso: pasarás a otra de las guardadas."
                            else "Es tu única ruta: la app quedará vacía hasta que importes otro Excel.",
                            fontSize = 13.sp, color = AppColors.Muted
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDelete(r.id, keepHistory); deleting = null }) {
                    Text(if (r.unexported > 0) "BORRAR DE TODOS MODOS" else "BORRAR", color = AppColors.Error, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Mis rutas") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (routes.isEmpty()) Text("Aún no hay rutas. Importa un Excel para empezar.", color = AppColors.Muted)
                routes.forEach { r ->
                    val fraction = if (r.total == 0) 0f else r.read.toFloat() / r.total
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (r.active) AppColors.Done.copy(alpha = 0.10f) else AppColors.Brand.copy(alpha = 0.06f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !r.active) { onSwitch(r.id) }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(
                                if (r.active) "● En uso" else "Cambiar ›",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (r.active) AppColors.Done else androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        }
                        Text("${r.read} de ${r.total} leídas", fontSize = 13.sp, color = AppColors.Muted)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = AppColors.Done,
                            trackColor = AppColors.Done.copy(alpha = 0.15f)
                        )
                        Text(
                            "Importada " + TimeUtil.dateTime(r.importedAt) + " · " +
                                    (r.lastExportAt?.let { "exportada " + TimeUtil.dateTime(it) } ?: "nunca exportada"),
                            fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 4.dp)
                        )
                        if (r.unexported > 0) {
                            Text("⚠ ${r.unexported} lecturas sin exportar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Warning)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { keepHistory = true; deleting = r }) {
                                Text("Borrar", color = AppColors.Error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Text(
                    "Para agregar otra ruta o actualizar una con el Excel del mes, importa el archivo.",
                    fontSize = 13.sp, color = AppColors.Muted
                )
            }
        },
        confirmButton = { TextButton(onClick = onImport) { Text("Importar Excel", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cerrar") } }
    )
}
