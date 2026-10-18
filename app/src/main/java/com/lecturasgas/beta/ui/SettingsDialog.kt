package com.lecturasgas.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import com.lecturasgas.beta.ui.icons.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.data.repository.AfterSave
import com.lecturasgas.beta.data.repository.AppSettings
import com.lecturasgas.beta.ui.theme.AppColors
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

/** Pantalla de ajustes: cada opción se activa o desactiva y se guarda al instante. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenBackup: () -> Unit,
    suspectCount: Int,
    onReviewLocations: () -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = AppColors.TopBar, contentColor = Color.White) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) { Icon(AppIcons.Back, contentDescription = "Volver", tint = Color.White) }
                        Text("Ajustes", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.height(2.dp))

                    Group(AppIcons.Palette, Color(0xFF7C4DFF), "Apariencia") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeTile(AppIcons.Sun, "Claro", settings.themeMode == "LIGHT", Modifier.weight(1f)) {
                                onChange { it.copy(themeMode = "LIGHT") }
                            }
                            ThemeTile(AppIcons.Moon, "Oscuro", settings.themeMode == "DARK", Modifier.weight(1f)) {
                                onChange { it.copy(themeMode = "DARK") }
                            }
                            ThemeTile(AppIcons.Phone, "Automático", settings.themeMode == "SYSTEM", Modifier.weight(1f)) {
                                onChange { it.copy(themeMode = "SYSTEM") }
                            }
                        }
                        Text(
                            "Automático sigue el modo del celular. En la calle con sol, el claro se lee mejor; de noche, el oscuro descansa la vista.",
                            fontSize = 13.sp, color = AppColors.Muted
                        )
                    }

                    Group(AppIcons.Phone, Color(0xFF00897B), "Pantalla y rendimiento") {
                        Text("Tamaño de la pantalla", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("AUTO" to "Automático", "NORMAL" to "Normal", "COMPACT" to "Compacto", "TINY" to "Muy compacto").forEach { (k, label) ->
                                FilterChip(
                                    selected = settings.uiScale == k,
                                    onClick = { onChange { it.copy(uiScale = k) } },
                                    label = { Text(label, fontSize = 14.sp) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        Text(
                            "Si en tu celular todo se ve muy grande o no cabe, prueba «Compacto». Automático lo achica solo en pantallas bajas.",
                            fontSize = 13.sp, color = AppColors.Muted
                        )
                        Text("Modo ligero (menos animaciones)", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("AUTO" to "Automático", "ON" to "Activado", "OFF" to "Desactivado").forEach { (k, label) ->
                                FilterChip(
                                    selected = settings.liteMode == k,
                                    onClick = { onChange { it.copy(liteMode = k) } },
                                    label = { Text(label, fontSize = 14.sp) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        Text(
                            "Para celulares viejos: quita animaciones y deja menos pantallas preparadas a la vez, así no se traba. Automático lo enciende en equipos con Android 9 o menor.",
                            fontSize = 13.sp, color = AppColors.Muted
                        )
                    }

                    Group(AppIcons.Pin, Color(0xFF1E6FD9), "Medidores cercanos") {
                        SwitchRow(
                            "Avisar cuando estoy cerca de un medidor",
                            "Muestra «Estás frente a NIR…» con el botón LEER.",
                            settings.nearEnabled
                        ) { v -> onChange { it.copy(nearEnabled = v) } }
                        if (settings.nearEnabled) {
                            Text("Distancia del aviso", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 25, 40, 60).forEach { m ->
                                    FilterChip(
                                        selected = settings.nearRadius == m,
                                        onClick = { onChange { it.copy(nearRadius = m) } },
                                        label = { Text("$m m", fontSize = 14.sp) },
                                        shape = RoundedCornerShape(50),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                            Text(
                                "Más corta = avisa solo cuando estás casi encima. Más larga = avisa desde antes.",
                                fontSize = 13.sp, color = AppColors.Muted
                            )
                            SwitchRow("Vibrar al detectar uno", "Una vibración cuando aparece un medidor nuevo.", settings.nearVibrate) { v ->
                                onChange { it.copy(nearVibrate = v) }
                            }
                            SwitchRow(
                                "Sugerir «Mejorar ubicación»",
                                "Si el punto guardado es dudoso, ofrece volver a medirlo.",
                                settings.nearWeakHint
                            ) { v -> onChange { it.copy(nearWeakHint = v) } }
                        }
                    }

                    Group(AppIcons.Vibrate, Color(0xFF2E9E5B), "Vibración") {
                        SwitchRow("Al guardar una lectura", "Confirma con una vibración que quedó guardada.", settings.vibrateSave) { v ->
                            onChange { it.copy(vibrateSave = v) }
                        }
                        SwitchRow("Al entregar una factura", "Confirma la entrega con una vibración.", settings.vibrateDeliver) { v ->
                            onChange { it.copy(vibrateDeliver = v) }
                        }
                        OutlinedButton(onClick = { runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }, shape = RoundedCornerShape(14.dp)) {
                            Text("Probar vibración")
                        }
                        Text(
                            "Si no vibra, revisa que «Vibración al tocar» esté activa en los ajustes de sonido del celular.",
                            fontSize = 13.sp, color = AppColors.Muted
                        )
                    }

                    Group(AppIcons.Pencil, Color(0xFF0B4F8A), "Lecturas") {
                        SwitchRow(
                            "Avisar si la lectura se ve rara",
                            "Menor que la anterior o con un consumo muy alto.",
                            settings.consumptionAlert
                        ) { v -> onChange { it.copy(consumptionAlert = v) } }
                        SwitchRow(
                            "Calibrar el GPS al leer (semáforo)",
                            "Encendido: ves el semáforo (verde = listo, amarillo = estabilizando, rojo = sin señal) y el punto del medidor solo se guarda en verde, más preciso. Apagado: lees rápido y el punto se guarda como siempre, sin esperar. La lectura se guarda igual en los dos modos.",
                            settings.calibrateGps
                        ) { v -> onChange { it.copy(calibrateGps = v) } }
                        SwitchRow(
                            "Grabar mi recorrido al leer",
                            "Al guardar la primera lectura del día se empieza a grabar por dónde caminas (rastro), igual que en Entrega. Gasta un poco más de batería. También puedes iniciarlo o terminarlo con el botón ▶ / ■ de la tarjeta de arriba.",
                            settings.trackReadings
                        ) { v -> onChange { it.copy(trackReadings = v) } }
                        SwitchRow(
                            "Guardar mi ubicación al leer",
                            "Marca/afina el punto del medidor con tu GPS.",
                            settings.saveGpsWithReading
                        ) { v -> onChange { it.copy(saveGpsWithReading = v) } }
                        Text("Después de guardar una lectura", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AfterSave.entries.forEach { a ->
                                FilterChip(
                                    selected = settings.afterSave == a,
                                    onClick = { onChange { it.copy(afterSave = a) } },
                                    label = { Text(a.label, fontSize = 14.sp) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    Group(AppIcons.Box, Color(0xFFD64545), "Entrega") {
                        SwitchRow(
                            "Mostrar el siguiente sugerido",
                            "Muestra el medidor que la app recomienda entregar. Apágalo si todavía no tienes la ruta calibrada: el buscador queda más arriba.",
                            settings.showNextCard
                        ) { v -> onChange { it.copy(showNextCard = v) } }
                        if (settings.showNextCard) SwitchRow(
                            "Sugerencia pequeña (una fila)",
                            "Ocupa poco espacio. Apagado, se ve la tarjeta grande con dirección, «Luego» y «Visitar después».",
                            settings.nextCompact
                        ) { v -> onChange { it.copy(nextCompact = v) } }
                        SwitchRow(
                            "Preguntar dónde dejé la factura",
                            "Al entregar se abre la nota de una vez. Si está apagado, entregas con un toque y la nota la agregas después.",
                            settings.askNoteOnDeliver
                        ) { v -> onChange { it.copy(askNoteOnDeliver = v) } }
                        SwitchRow(
                            "Afinar ubicación al entregar",
                            "Mide unos segundos de GPS en la puerta para mejorar el punto.",
                            settings.refineOnDeliver
                        ) { v -> onChange { it.copy(refineOnDeliver = v) } }
                    }

                    Group(AppIcons.Phone, Color(0xFFF08A1C), "Pantalla y mapa") {
                        SwitchRow(
                            "Mantener la pantalla encendida",
                            "Útil en la calle. Gasta más batería. (Siempre queda encendida durante una jornada de entrega.)",
                            settings.keepScreenOn
                        ) { v -> onChange { it.copy(keepScreenOn = v) } }
                        SwitchRow(
                            "Brújula en el mapa",
                            "Con «Gira», el mapa también se orienta con la brújula del celular cuando estás parado.",
                            settings.useCompass
                        ) { v -> onChange { it.copy(useCompass = v) } }
                    }

                    Group(AppIcons.Database, Color(0xFF6B7785), "Datos") {
                        SwitchRow(
                            "Revisar antes de exportar",
                            "Al tocar Exportar, primero se muestra una lista de lo raro: lecturas menores a la anterior, consumos exagerados, lecturas del Excel que se cambian y medidores sin leer.",
                            settings.reviewBeforeExport
                        ) { v -> onChange { it.copy(reviewBeforeExport = v) } }
                        SwitchRow(
                            "Comparar con el original al exportar",
                            "Encendido: más seguro, pero tarda más. Apagado: exporta rápido y no revisa el archivo (siempre solo se escribe la columna de lectura). Puedes comprobarlo después desde el menú ☰ → Comprobar exportación.",
                            settings.verifyOnExport
                        ) { v -> onChange { it.copy(verifyOnExport = v) } }
                        OutlinedButton(onClick = onReviewLocations, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text(if (suspectCount > 0) "Revisar ubicaciones sospechosas ($suspectCount)" else "Revisar ubicaciones")
                        }
                        OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text("Respaldo y traspaso a otro celular")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(icon: ImageVector, tint: Color, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(subtitle, fontSize = 13.sp, color = AppColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else AppColors.CardAlt)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val c = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c)
    }
}
