package com.lecturasgas.beta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colores pensados para trabajo en la calle: alto contraste para leer
 * bajo el sol, y un significado fijo para cada estado.
 */
object AppColors {
    /** true cuando la app está en modo oscuro (lo fija LecturasTheme). Es estado de Compose: al cambiar, todo se repinta. */
    var dark by mutableStateOf(false)
        internal set

    private fun pick(light: Long, darkC: Long) = Color(if (dark) darkC else light)

    val Brand get() = pick(0xFF0B4F8A, 0xFF4C9AE8)
    val BrandDark get() = pick(0xFF073763, 0xFF0A3A66)
    val Accent get() = Color(0xFFF28C28)
    val Pending get() = pick(0xFF7B3FE4, 0xFFA77BFF)    // 🟣 pendiente
    val Done get() = pick(0xFF1E8E3E, 0xFF4CC472)       // 🟢 leído
    val Next get() = Color(0xFFF28C28)                  // 🟠 siguiente
    val Delivered get() = pick(0xFFC62828, 0xFFFF6B6B)  // 🔴 entregado
    val Skipped get() = pick(0xFF8D6E63, 0xFFB59A90)    // 🟤 sin leer
    val Warning get() = pick(0xFFB26A00, 0xFFFFB454)
    val Error get() = pick(0xFFC62828, 0xFFFF6B6B)
    val Muted get() = pick(0xFF4A5766, 0xFF9FB0C3)
    /** Fondo de pantalla. */
    val Surface get() = pick(0xFFF4F6F9, 0xFF0D1520)
    /** Tarjetas y paneles. */
    val Card get() = pick(0xFFFFFFFF, 0xFF16222F)
    /** Tarjeta un poco más hundida (chips, campos). */
    val CardAlt get() = pick(0xFFE7ECF2, 0xFF1F2E3E)
    /** Barra superior. */
    val TopBar get() = pick(0xFF0B4F8A, 0xFF101B28)
    val Divider get() = pick(0xFFDDE3EA, 0xFF2A3A4C)
    val TintGreen get() = pick(0xFFE8F5E9, 0xFF16301F)
    val TintOrange get() = pick(0xFFFFF5EA, 0xFF3A2A14)
    val TintViolet get() = pick(0xFFF1EAFE, 0xFF2A2145)
    /** Fondo del aro de los anillos de avance. */
    val RingTrack get() = pick(0xFFE4EAF1, 0xFF2A3A4C)
    /** Texto principal. */
    val Ink get() = pick(0xFF1B2430, 0xFFE8EEF5)

    const val PENDING_HEX = "#7B3FE4"
    const val DONE_HEX = "#1E8E3E"
    const val NEXT_HEX = "#F28C28"
    const val DELIVERED_HEX = "#C62828"
    const val SKIPPED_HEX = "#8D6E63"
}

private val lightScheme = lightColorScheme(
    primary = Color(0xFF0B4F8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = Color(0xFF073763),
    secondary = Color(0xFFF28C28),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3C7),
    onSecondaryContainer = Color(0xFF4A2600),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF1B2430),
    surface = Color.White,
    onSurface = Color(0xFF1B2430),
    surfaceVariant = Color(0xFFE7ECF2),
    onSurfaceVariant = Color(0xFF3F4A57),
    outline = Color(0xFF7A8794),
    outlineVariant = Color(0xFFDDE3EA),
    error = Color(0xFFC62828)
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF4C9AE8),
    onPrimary = Color(0xFF04213D),
    primaryContainer = Color(0xFF16385C),
    onPrimaryContainer = Color(0xFFD6E6F7),
    secondary = Color(0xFFF28C28),
    onSecondary = Color(0xFF2B1600),
    secondaryContainer = Color(0xFF5A3410),
    onSecondaryContainer = Color(0xFFFFE3C7),
    background = Color(0xFF0D1520),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF16222F),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF1F2E3E),
    onSurfaceVariant = Color(0xFFB4C2D1),
    outline = Color(0xFF6B7C8F),
    outlineVariant = Color(0xFF2A3A4C),
    error = Color(0xFFFF6B6B)
)

private val base = Typography()
private val typography = Typography(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp),
    labelMedium = base.labelMedium.copy(fontSize = 13.sp),
    labelSmall = base.labelSmall.copy(fontSize = 13.sp)
)

/** Modo de apariencia que elige el usuario en Ajustes. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun LecturasTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // Se fija antes de componer el contenido para que los colores dinámicos ya sean los correctos.
    AppColors.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkScheme else lightScheme,
        typography = typography,
        shapes = androidx.compose.material3.Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
        ),
        content = content
    )
}

val MeterCodeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.5.sp)
