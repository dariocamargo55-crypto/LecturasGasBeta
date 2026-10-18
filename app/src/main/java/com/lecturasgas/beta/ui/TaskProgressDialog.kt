package com.lecturasgas.beta.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lecturasgas.beta.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Ventana de progreso para las tareas largas con etapas (comparar, comprobar la exportación, exportar).
 *
 *  - Anillo con el porcentaje que avanza suave (no a saltos) y un «cometa» que gira siempre: se nota que la app
 *    sigue trabajando aunque el porcentaje se quede un rato en el mismo número.
 *  - Barra con brillo que corre de lado a lado.
 *  - Etapa actual con animación de cambio, tiempo transcurrido y estimación de lo que falta.
 *  - Botón «Cancelar»: la tarea se detiene sin dejar archivos a medias. Se desactiva cuando ya se está guardando el
 *    archivo final.
 */
@Composable
fun TaskProgressDialog(busy: MainViewModel.Busy, onCancel: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val target = busy.fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, animationSpec = tween(500, easing = FastOutSlowInEasing), label = "avance")

    val infinite = rememberInfiniteTransition(label = "trabajando")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "giro")
    val spinBack by infinite.animateFloat(360f, 0f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "giro2")
    val shimmer by infinite.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "brillo")
    val pulse by infinite.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulso")

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(busy.startedAt) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val elapsed = if (busy.startedAt > 0) ((now - busy.startedAt) / 1000).toInt().coerceAtLeast(0) else 0
    val remaining = if (elapsed >= 4 && target in 0.08f..0.97f) (elapsed * (1f - target) / target).roundToInt() else -1

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        AnimatedVisibility(entered, enter = fadeIn(tween(250)) + scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 0.88f)) {
            Surface(shape = RoundedCornerShape(28.dp), color = AppColors.Card, tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(busy.title, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))

                    Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(156.dp)) {
                            val stroke = 12.dp.toPx()
                            val thin = 3.dp.toPx()
                            // Anillo exterior: dos arcos que giran en sentido contrario (movimiento permanente).
                            val outer = Size(size.width - thin, size.height - thin)
                            val outerTopLeft = Offset(thin / 2, thin / 2)
                            drawArc(primary.copy(alpha = 0.55f * pulse), spin, 70f, false, outerTopLeft, outer, style = Stroke(thin, cap = StrokeCap.Round))
                            drawArc(primary.copy(alpha = 0.30f), spinBack, 40f, false, outerTopLeft, outer, style = Stroke(thin, cap = StrokeCap.Round))
                            // Anillo del avance real.
                            val inset = 14.dp.toPx() + stroke / 2
                            val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
                            val innerTopLeft = Offset(inset, inset)
                            drawArc(primary.copy(alpha = 0.15f), 0f, 360f, false, innerTopLeft, inner, style = Stroke(stroke))
                            if (shown > 0f) {
                                drawArc(primary, -90f, 360f * shown, false, innerTopLeft, inner, style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${(shown * 100).roundToInt()}", fontWeight = FontWeight.Black, fontSize = 40.sp)
                            Text(" %", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    // Barra con brillo que corre.
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(primary.copy(alpha = 0.15f))) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(shown.coerceAtLeast(0.03f)).clip(RoundedCornerShape(50)).background(primary)
                                .drawWithContent {
                                    drawContent()
                                    val w = size.width
                                    val x = shimmer * w
                                    drawRect(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent),
                                            startX = x - w * 0.25f, endX = x + w * 0.25f
                                        )
                                    )
                                }
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    AnimatedContent(
                        targetState = busy.stage.ifEmpty { "Un momento…" },
                        transitionSpec = {
                            (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 })
                        },
                        label = "etapa"
                    ) { stage ->
                        Text(stage, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(formatSeconds(elapsed), fontSize = 13.sp, color = AppColors.Muted)
                        if (remaining >= 0) {
                            Text("  ·  faltan ≈ ${formatSeconds(remaining)}", fontSize = 13.sp, color = AppColors.Muted)
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    if (busy.cancelable) {
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !busy.cancelling,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(if (busy.cancelling) "Cancelando…" else "Cancelar", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Si cancelas, no se guarda nada y puedes repetirlo cuando tengas tiempo.",
                            fontSize = 12.sp, color = AppColors.Muted, textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            "Guardando el archivo: esperemos unos segundos, ya no se puede cancelar.",
                            fontSize = 12.sp, color = AppColors.Muted, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun formatSeconds(total: Int): String =
    if (total < 60) "$total s" else "${total / 60} min ${(total % 60).toString().padStart(2, '0')} s"
