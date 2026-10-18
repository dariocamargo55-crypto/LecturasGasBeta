package com.lecturasgas.beta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.SearchField
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.ui.theme.MeterCodeStyle
import kotlinx.coroutines.delay

/** Etiqueta pequeña de color (estado). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.13f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/**
 * Tarjeta de un medidor. Muestra lo necesario para reconocerlo sin abrirlo:
 * NIR, medidor, usuario, dirección, barrio y la lectura.
 */
@Composable
fun MeterCard(
    record: MeterRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    statusText: String? = null,
    statusColor: Color? = null,
    showReading: Boolean = true,
    extraLine: String? = null,
    /** Muestra las marcas de «NUEVO» y «NIR anterior» (por defecto, mientras no tenga lectura). */
    fresh: Boolean = !record.isRead,
    actions: (@Composable () -> Unit)? = null
) {
    val (pillText, pillColor) = when {
        statusText != null -> statusText to (statusColor ?: AppColors.Muted)
        record.appReading != null -> "Leída" to AppColors.Done
        record.excelReading != null -> "En Excel" to AppColors.Done
        record.skipReason != null -> record.skipReason to AppColors.Skipped
        else -> "Pendiente" to AppColors.Pending
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else AppColors.Card
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(end = 12.dp)) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(pillColor)
            )
            Column(Modifier.padding(start = 11.dp, top = 9.dp, bottom = 9.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "NIR ${record.nir.ifBlank { "—" }}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    if (pillText.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill(pillText, pillColor)
                    }
                    if (record.isNew && fresh) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("✦ NUEVO", AppColors.Accent)
                    }
                    if (!record.hasLocation) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("Sin ubicación", AppColors.Muted)
                    }
                    if (record.note.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("Nota", AppColors.Warning)
                    }
                }
                Text(record.displayMeter, style = MeterCodeStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (record.prevNir != null && fresh) {
                    Text(
                        "NIR de antes: ${record.prevNir}",
                        fontSize = 13.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (record.hasMeterFix) {
                    Text(
                        "Corregido · en el Excel dice ${record.meter}",
                        fontSize = 13.sp, color = AppColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    record.user.ifBlank { "Sin usuario" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(record.address, record.barrio).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (showReading) {
                    val consumption = record.consumption
                    Text(
                        buildString {
                            append("Anterior ${record.previous ?: "—"}")
                            record.reading?.let { append("   →   Actual $it") }
                            if (consumption != null) append("   (${if (consumption >= 0) "+" else ""}$consumption)")
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (consumption != null && consumption < 0) AppColors.Error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (extraLine != null) {
                    Text(extraLine, fontSize = 13.sp, color = AppColors.Muted)
                }
            }
            if (actions != null) {
                Box(Modifier.align(Alignment.CenterVertically).padding(start = 6.dp)) { actions() }
            }
        }
    }
}

/**
 * Caja de búsqueda con selector de campo y botón 123 / ABC.
 * El campo y el tipo de teclado se recuerdan hasta que el usuario los cambie.
 */
@Composable
fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    fields: Set<SearchField>,
    onFieldToggle: (SearchField) -> Unit,
    numeric: Boolean,
    onNumericChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    resultText: String? = null,
    /** Cada vez que este número sube, el campo toma el foco y abre el teclado. */
    focusSignal: Int = 0,
    /** Línea pequeña bajo el buscador (por ejemplo "Última lectura: ..."). */
    footer: (@Composable () -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var reopenKeyboard by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // key(numeric): al cambiar 123/ABC se recrea el campo para que
            // Android cambie de teclado inmediatamente.
            key(numeric) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Buscar por " + SearchField.entries.filter { it in fields }.joinToString(" o ") { it.label.lowercase() } + "…",
                            maxLines = 1
                        )
                    },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Borrar")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    reopenKeyboard = focused
                    onNumericChange(!numeric)
                },
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(if (numeric) "123" else "ABC", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        LaunchedEffect(focusSignal) {
            if (focusSignal > 0) {
                delay(220)
                runCatching { focusRequester.requestFocus() }
                keyboard?.show()
            }
        }
        LaunchedEffect(numeric) {
            if (reopenKeyboard) {
                delay(60)
                runCatching { focusRequester.requestFocus() }
                keyboard?.show()
                reopenKeyboard = false
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            items(SearchField.entries.toList()) { f ->
                FilterChip(
                    selected = f in fields,
                    onClick = { onFieldToggle(f) },
                    label = { Text(f.label, fontSize = 14.sp) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        if (resultText != null) {
            Text(resultText, fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(bottom = 2.dp))
        }
        if (footer != null) footer()
    }
}

/** Barra de progreso con texto "1289 / 2021". */
@Composable
fun ProgressStrip(done: Int, total: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$done", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(" / $total $label", fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text("${(fraction * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

/** Botón grande para usar con una sola mano. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Selector segmentado simple (Pendientes | Leídas | Todas). */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, color = AppColors.Muted)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.Bold) }
        }
    }
}
