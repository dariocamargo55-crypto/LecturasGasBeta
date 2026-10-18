package com.lecturasgas.beta.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.ui.icons.AppIcons
import com.lecturasgas.beta.ui.theme.AppColors
import kotlinx.coroutines.delay

/** Una fila de una lista completa (medidores sin GPS, notas, etc.). */
class ListEntry(
    val title: String,
    val subtitle: String = "",
    val extra: String = "",
    /** Botones pequeños dentro de la tarjeta (ej. «Borrar punto»). No cierran la lista. */
    val actions: List<EntryAction> = emptyList(),
    val onClick: (() -> Unit)? = null
)

class EntryAction(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * Lista completa a pantalla completa: encabezado de color, buscador y tarjetas que entran en
 * cascada. Al tocar una tarjeta se ejecuta su acción y se cierra.
 */
@Composable
fun ListDialog(
    title: String,
    hint: String,
    entries: List<ListEntry>,
    color: Color = AppColors.TopBar,
    icon: ImageVector? = null,
    emptyText: String = "Nada por aquí.",
    /** Botón opcional en el encabezado (ej. «Borrar todos»). */
    headerAction: EntryAction? = null,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(entries, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) entries
        else entries.filter { (it.title + " " + it.subtitle + " " + it.extra).lowercase().contains(q) }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = color, contentColor = Color.White) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onClose) { Text("‹ Cerrar", color = Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.weight(1f))
                            if (headerAction != null && entries.isNotEmpty()) {
                                TextButton(onClick = headerAction.onClick) {
                                    Text(headerAction.label, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                "${entries.size}", fontWeight = FontWeight.Black, fontSize = 15.sp,
                                modifier = Modifier.background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) {
                                Box(
                                    Modifier.size(42.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (hint.isNotBlank()) Text(hint, fontSize = 13.sp, color = Color.White.copy(alpha = 0.88f))
                            }
                        }
                    }
                }
                if (entries.size > 6) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Buscar…") }, singleLine = true,
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                if (shown.isEmpty()) {
                    Text(emptyText, color = AppColors.Muted, modifier = Modifier.padding(20.dp))
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(shown) { i, e ->
                            EntryCard(e, i, color, icon) { e.onClick?.invoke(); onClose() }
                        }
                        item { Spacer(Modifier.navigationBarsPadding()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(e: ListEntry, index: Int, color: Color, icon: ImageVector?, onClick: () -> Unit) {
    // Las primeras tarjetas entran en cascada; las demás aparecen de una vez.
    val a = remember { Animatable(if (index < 12) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (index < 12) {
            delay(index * 45L)
            a.animateTo(1f, tween(380))
        }
    }
    val letter = e.title.removePrefix("NIR ").trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 40f }
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Card, RoundedCornerShape(18.dp))
            .let { m -> if (e.onClick != null) m.clickable(onClick = onClick) else m }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(color.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            else Text(letter, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.subtitle.isNotBlank()) Text(e.subtitle, fontSize = 13.sp, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.extra.isNotBlank()) {
                Text(
                    e.extra, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            if (e.actions.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    e.actions.forEach { a ->
                        TextButton(onClick = a.onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(a.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (a.danger) AppColors.Error else color)
                        }
                    }
                }
            }
        }
        if (e.onClick != null) Icon(AppIcons.Chevron, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
    }
}
