package com.lecturasgas.beta.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lecturasgas.beta.ui.LocalLite
import com.lecturasgas.beta.ui.theme.AppColors

/**
 * Anillo de avance: aro redondeado con degradado, un resplandor suave y un punto en la punta
 * (los anillos pequeños se dibujan simples). Al aparecer se llena con una animación corta.
 * Lo que se pone dentro (porcentaje, texto) va en [content], centrado.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = AppColors.RingTrack,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val lite = LocalLite.current
    var started by remember { mutableStateOf(lite) }
    LaunchedEffect(Unit) { started = true }
    val target = if (started) fraction.coerceIn(0f, 1f) else 0f
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = if (lite) snap() else tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "ring"
    )
    val fancy = size >= 64.dp && !lite          // los anillos pequeños se quedan simples
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val middle = Offset(this.size.width / 2f, this.size.height / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (shown > 0f) {
                rotate(degrees = -90f, pivot = middle) {
                    if (fancy) {
                        // Resplandor suave detrás del arco.
                        drawArc(
                            color = color.copy(alpha = 0.16f),
                            startAngle = 0f, sweepAngle = 360f * shown, useCenter = false,
                            topLeft = Offset(stroke * 0.05f, stroke * 0.05f),
                            size = Size(this.size.width - stroke * 0.1f, this.size.height - stroke * 0.1f),
                            style = Stroke(width = stroke * 1.9f, cap = StrokeCap.Round)
                        )
                    }
                    // El arco se aclara al empezar y se hace firme al final (degradado).
                    val brush = if (fancy && shown > 0.02f) Brush.sweepGradient(
                        0f to color.copy(alpha = 0.55f),
                        shown.coerceAtMost(0.999f) to color,
                        1f to color,
                        center = middle
                    ) else null
                    if (brush != null) {
                        drawArc(
                            brush = brush,
                            startAngle = 0f, sweepAngle = 360f * shown, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    } else {
                        drawArc(
                            color = color,
                            startAngle = 0f, sweepAngle = 360f * shown, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                // Punto blanco en la punta del arco.
                if (fancy && shown > 0.03f && shown < 0.995f) {
                    val a = Math.toRadians((-90.0 + 360.0 * shown))
                    val r = (this.size.width - stroke) / 2f
                    val dot = Offset(middle.x + (r * Math.cos(a)).toFloat(), middle.y + (r * Math.sin(a)).toFloat())
                    drawCircle(Color.White.copy(alpha = 0.92f), radius = stroke * 0.26f, center = dot)
                }
            }
        }
        content()
    }
}
