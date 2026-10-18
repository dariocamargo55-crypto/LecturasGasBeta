package com.lecturasgas.beta.readings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import com.lecturasgas.beta.ui.components.Dot
import com.lecturasgas.beta.location.Light
import com.lecturasgas.beta.location.GpsQualityRules
import com.lecturasgas.beta.location.GpsQuality
import com.lecturasgas.beta.ui.icons.AppIcons
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.SkipReasons
import com.lecturasgas.beta.data.repository.AfterSave
import com.lecturasgas.beta.ui.components.BigButton
import com.lecturasgas.beta.ui.components.Segmented
import com.lecturasgas.beta.ui.components.StatusPill
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.ui.theme.MeterCodeStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Registrar lectura: pensado para hacerlo en 2 toques.
 * 1) escribir el número (el teclado numérico se abre solo)
 * 2) "Guardar": y eliges qué pasa después (buscar otro medidor, abrir el siguiente en ruta, o solo cerrar).
 */
@Composable
fun ReadingDialog(
    record: MeterRecord,
    fix: GeoFix?,
    quality: GpsQuality,
    calibrate: Boolean,
    initialSaveGps: Boolean,
    initialAfter: AfterSave,
    onDismiss: () -> Unit,
    onSave: (value: Long, note: String, saveGps: Boolean, after: AfterSave) -> Unit,
    onClear: () -> Unit,
    /** Corrige el número del medidor SOLO en la app (null = quitar la corrección). */
    onSetMeterFix: (String?) -> Unit,
    meterFixWarning: (String) -> String?,
    /** Consumo típico de la ruta (para avisar de lecturas raras). Null si aún no se conoce. */
    typicalConsumption: Long? = null,
    consumptionAlert: Boolean = true,
    /** "No se pudo leer": motivo + nota + qué hacer después. */
    onSkip: (reason: String, note: String, after: AfterSave) -> Unit,
    onClearSkip: () -> Unit,
    onShowMap: () -> Unit,
    onAdjustLocation: () -> Unit,
    /** Abre el historial de este medidor (meses anteriores y cambios). */
    onHistory: () -> Unit = {}
) {
    // Si el medidor ya tiene lectura (de la app o del Excel), se muestra en la casilla y queda
    // seleccionada: escribir encima la reemplaza (así se corrige una lectura mal tomada).
    val initialText = (record.appReading ?: record.excelReading)?.toString() ?: ""
    var text by remember(record.row, record.appReading) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }
    var note by remember(record.row) { mutableStateOf(record.note) }
    var showNote by remember(record.row) { mutableStateOf(record.note.isNotBlank()) }
    var saveGps by remember { mutableStateOf(initialSaveGps) }
    var after by remember { mutableStateOf(initialAfter) }
    var needsConfirm by remember(record.row) { mutableStateOf(false) }
    var showFix by remember(record.row) { mutableStateOf(false) }
    var fixText by remember(record.row) { mutableStateOf(record.realMeter ?: "") }
    var showSkip by remember(record.row) { mutableStateOf(false) }
    var confirmDelete by remember(record.row) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(record.row) {
        delay(180)
        runCatching { focus.requestFocus() }
    }

    val value = text.text.toLongOrNull()
    val previous = record.previous
    val consumption = if (value != null && previous != null) value - previous else null
    val warning: String? = if (!consumptionAlert) null else value?.let { ConsumptionCheck.warning(it, previous, typicalConsumption) }

    fun trySave() {
        val v = value ?: return
        if (warning != null && !needsConfirm) {
            needsConfirm = true
            return
        }
        onSave(v, note.trim(), saveGps, after)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .imePadding(),
            shape = RoundedCornerShape(26.dp),
            color = AppColors.Card,
            shadowElevation = 12.dp,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // ---------- Encabezado ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NIR ${record.nir}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    when {
                        record.appReading != null -> StatusPill("Leída en app", AppColors.Done)
                        record.excelReading != null -> StatusPill("Ya viene en el Excel", AppColors.Done)
                        record.skipReason != null -> StatusPill(record.skipReason, AppColors.Skipped)
                        else -> StatusPill("Pendiente", AppColors.Pending)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(AppIcons.Close, contentDescription = "Cerrar", modifier = Modifier.size(22.dp))
                    }
                }
                Text(record.displayMeter, style = MeterCodeStyle.copy(fontSize = 26.sp))
                if (record.hasMeterFix) {
                    Text("Corregido · en el Excel dice ${record.meter}", fontSize = 12.sp, color = AppColors.Warning)
                }
                Text(record.user, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(record.address, record.barrio).filter { it.isNotBlank() }.joinToString(" · "),
                    color = AppColors.Muted, fontSize = 13.sp
                )
                if (record.observation.isNotBlank()) {
                    Text(
                        "Obs. Excel: ${record.observation}", color = AppColors.Warning, fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(AppColors.TintOrange, RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }

                // ---------- Anterior / consumo ----------
                Spacer(Modifier.height(10.dp))
                Row {
                    InfoBox("Anterior", previous?.toString() ?: "—", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    InfoBox(
                        "Consumo",
                        consumption?.let { (if (it >= 0) "+" else "") + it } ?: "—",
                        Modifier.weight(1f),
                        color = when {
                            consumption == null -> AppColors.Muted
                            consumption < 0 -> AppColors.Error
                            consumption > ConsumptionCheck.highThreshold(typicalConsumption) -> AppColors.Warning
                            else -> AppColors.Done
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onHistory,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(AppIcons.Clock, contentDescription = "Historial del medidor", tint = AppColors.Muted, modifier = Modifier.size(22.dp))
                            Text("Historial", fontSize = 11.sp, color = AppColors.Muted, maxLines = 1)
                        }
                    }
                }
                if (record.excelReading != null && record.appReading == null) {
                    Text(
                        "Lectura en el Excel: ${record.excelReading}. Si escribes otra, se corrige al exportar.",
                        fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (record.appReading != null) {
                    Text(
                        if (value != null && value != record.appReading)
                            "Guardada: ${record.appReading} → cambiará a $value al guardar"
                        else "Lectura guardada: ${record.appReading}. Escribe otra para corregirla.",
                        fontSize = 12.sp,
                        color = if (value != null && value != record.appReading) AppColors.Warning else AppColors.Muted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // ---------- Lectura + botón de guardar ----------
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v ->
                            val digits = v.text.filter { it.isDigit() }.take(9)
                            text = TextFieldValue(digits, TextRange(digits.length))
                            needsConfirm = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                        label = { Text("Lectura actual") },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { trySave() }),
                        shape = RoundedCornerShape(16.dp),
                        isError = warning != null
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val ready = value != null
                        Box(
                            Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !ready -> AppColors.CardAlt
                                        needsConfirm -> AppColors.Warning
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                                .clickable(enabled = ready) { trySave() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.Check,
                                contentDescription = "Guardar lectura",
                                tint = if (ready) Color.White else AppColors.Muted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            if (needsConfirm) "Confirmar" else "Guardar",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (ready) MaterialTheme.colorScheme.onSurface else AppColors.Muted
                        )
                    }
                }
                if (warning != null) {
                    Text(warning, color = AppColors.Error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }

                // ---------- Fichas: GPS y qué pasa después ----------
                Spacer(Modifier.height(8.dp))
                GpsChip(
                    quality = quality,
                    fix = fix,
                    calibrate = calibrate,
                    saveGps = saveGps,
                    toggleable = !record.hasLocation,
                    onToggle = { saveGps = !saveGps }
                )
                Spacer(Modifier.height(6.dp))
                Box {
                    var afterOpen by remember { mutableStateOf(false) }
                    Chip(onClick = { afterOpen = true }) {
                        Text("Después de guardar: ${after.label} ▾", fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = afterOpen, onDismissRequest = { afterOpen = false }) {
                        AfterSave.entries.forEach { a ->
                            DropdownMenuItem(
                                text = { Text(a.label, fontWeight = if (a == after) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { after = a; afterOpen = false }
                            )
                        }
                    }
                }

                // ---------- Nota (visible si hay o si la pidieron) ----------
                if (showNote) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text("Nota (solo en la app, no va al Excel)") },
                        placeholder = { Text("Ej: medidor físico diferente, perro, no hay acceso…") }
                    )
                }

                // ---------- Fila de acciones ----------
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().background(Color.Transparent),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ActionIcon(AppIcons.Pencil, if (record.note.isNotBlank() || note.isNotBlank()) "Nota ●" else "Nota", Modifier.weight(1f)) {
                        showNote = !showNote
                    }
                    if (record.hasLocation) {
                        ActionIcon(AppIcons.Pin, "Mapa", Modifier.weight(1f), onClick = onShowMap)
                    }
                    ActionIcon(AppIcons.Locate, if (record.hasLocation) "Ajustar GPS" else "Ubicar", Modifier.weight(1f), onClick = onAdjustLocation)
                    ActionIcon(AppIcons.ListLines, if (record.hasMeterFix) "Otro N.º ●" else "Otro N.º", Modifier.weight(1f)) {
                        showFix = !showFix
                    }
                    if (!record.isRead) {
                        ActionIcon(AppIcons.Warning, "No se pudo", Modifier.weight(1f)) { showSkip = !showSkip }
                    }
                    ActionIcon(
                        AppIcons.Trash, "Borrar", Modifier.weight(1f),
                        danger = true, enabled = record.appReading != null
                    ) { confirmDelete = true }
                }

                // ---------- ¿El número del medidor es otro? (solo cambia en la app) ----------
                if (showFix) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fixText,
                        onValueChange = { v -> fixText = v.filter { it.isLetterOrDigit() || it == '-' }.take(24).uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Número REAL del medidor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Text
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    meterFixWarning(fixText)?.let {
                        Text(it, color = AppColors.Warning, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        "Solo se guarda en la app. Al buscar este número aparecerá este usuario; el Excel queda igual.",
                        fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSetMeterFix(fixText); showFix = false },
                            enabled = fixText.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Guardar corrección") }
                        if (record.hasMeterFix) {
                            OutlinedButton(onClick = { onSetMeterFix(null); fixText = ""; showFix = false }) { Text("Quitar") }
                        }
                    }
                }

                // ---------- No se pudo leer ----------
                if (!record.isRead) {
                    if (record.skipReason != null && !showSkip) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                "Motivo: ${record.skipReason}",
                                color = AppColors.Skipped, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearSkip) { Text("Quitar") }
                        }
                    }
                    if (showSkip) {
                        SkipReasons.all.forEach { reason ->
                            OutlinedButton(
                                onClick = { onSkip(reason, note.trim(), after) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) { Text(reason) }
                        }
                        Text(
                            "Queda anotado y el punto se ve distinto en el mapa. No cambia el Excel.",
                            fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar la lectura?") },
            text = {
                Text(
                    "Se borrará la lectura ${record.appReading} del medidor ${record.displayMeter}. " +
                        "Quedará como pendiente. La nota, la ubicación GPS y el Excel no cambian."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onClear() }) {
                    Text("Borrar", color = AppColors.Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun InfoBox(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, fontSize = 11.sp, color = AppColors.Muted)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}


/** Ficha pequeña (fondo suave) que se puede tocar. */
@Composable
private fun Chip(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    background: Color = AppColors.CardAlt,
    content: @Composable () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** Botón con icono y nombre debajo, para la fila de acciones. */
@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = if (danger) AppColors.Error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = c, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = if (danger) AppColors.Error else AppColors.Muted, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * GPS en una sola ficha: el color es el semáforo (verde = ya puedes guardar el punto del medidor;
 * amarillo = se estabiliza; rojo = sin señal). Si el medidor aún no tiene ubicación, la ficha también
 * es el interruptor de «guardar mi ubicación». La lectura SIEMPRE se puede guardar.
 */
@Composable
private fun GpsChip(
    quality: GpsQuality,
    fix: GeoFix?,
    calibrate: Boolean,
    saveGps: Boolean,
    toggleable: Boolean,
    onToggle: () -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val acc = fix?.accuracy
    val stale = now - quality.time > GpsQualityRules.STALE_MS
    val light = if (stale) Light.RED else quality.light
    val useLight = calibrate && saveGps
    val color = if (useLight) when (light) {
        Light.GREEN -> AppColors.Done
        Light.YELLOW -> AppColors.Warning
        Light.RED -> AppColors.Error
    } else when {
        fix == null -> AppColors.Warning
        acc != null && acc > 40f -> AppColors.Warning
        else -> AppColors.Done
    }
    val state = if (useLight) (if (stale) "Sin señal GPS" else quality.reason) else when {
        fix == null -> "esperando señal…"
        acc != null && acc > 40f -> "señal débil ±${acc.roundToInt()} m"
        acc != null -> "±${acc.roundToInt()} m"
        else -> "activo"
    }
    // Si el medidor ya tiene ubicación y el semáforo está apagado, no hay nada que mostrar.
    if (!toggleable && !useLight) return
    Column {
        Chip(
            onClick = if (toggleable) onToggle else null,
            background = if (saveGps) color.copy(alpha = 0.14f) else AppColors.CardAlt
        ) {
            Dot(color, 12.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    toggleable && saveGps -> "Guardar ubicación · $state ✓"
                    toggleable -> "☐ Guardar mi ubicación GPS aquí"
                    else -> "GPS · $state"
                },
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (saveGps) color else MaterialTheme.colorScheme.onSurface
            )
        }
        if (useLight && light != Light.GREEN) {
            Text(
                "Guardo la lectura, pero el punto del medidor solo se guarda con el GPS en verde.",
                fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(start = 6.dp, top = 3.dp)
            )
        }
    }
}
