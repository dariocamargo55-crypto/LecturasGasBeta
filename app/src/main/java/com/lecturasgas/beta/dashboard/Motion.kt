package com.lecturasgas.beta.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.lecturasgas.beta.ui.LocalLite
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Aparece desde abajo con un pequeño retraso según su [index] (para que todo entre en cascada). */
fun Modifier.appear(index: Int, stepMs: Long = 80L): Modifier = composed {
    val lite = LocalLite.current
    val a = remember { Animatable(if (lite) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!lite) {
            delay(index * stepMs)
            a.animateTo(1f, tween(450))
        }
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 36f
    }
}

/** Un valor que sube y baja suavemente (para latidos, puntos que respiran). */
@Composable
fun rememberPulse(from: Float = 0.55f, to: Float = 1f, periodMs: Int = 1100): State<Float> {
    // Modo ligero: sin animación infinita (gasta batería y fluidez en celulares viejos).
    if (LocalLite.current) return remember(to) { mutableStateOf(to) }
    val t = rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(
        initialValue = from, targetValue = to,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseValue"
    )
}
