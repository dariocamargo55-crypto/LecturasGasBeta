package com.lecturasgas.beta.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Iconos propios de la app, dibujados como líneas (trazo de 2) para que todos
 * se vean de la misma familia. No necesitan ninguna librería: cada uno es una
 * lista de trazos SVG sencillos. Se usan con Icon(AppIcons.Home, contentDescription = null, tint = ...).
 */
object AppIcons {

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    // ---- Navegación ----
    val Home by lazy { icon("Home", "M3 11l9-8 9 8v10h-6v-6H9v6H3z") }
    val Pencil by lazy { icon("Pencil", "M4 20l1-4L16 5l3 3L8 19z", "M14 7l3 3") }
    val Pin by lazy { icon("Pin", "M12 21s7-6.2 7-11a7 7 0 0 0-14 0c0 4.8 7 11 7 11z", "M12 7.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z") }
    val Trash by lazy { icon("Trash", "M5 7h14", "M9 7V4h6v3", "M7 7l1 13h8l1-13") }
    val Check by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7") }
    val CheckCircle by lazy { icon("CheckCircle", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M8 12.5l3 3 5-6") }

    // ---- Acciones ----
    val Search by lazy { icon("Search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-4-4") }
    val Back by lazy { icon("Back", "M15 5l-7 7 7 7") }
    val Chevron by lazy { icon("Chevron", "M9 5l7 7-7 7") }
    val Menu by lazy { icon("Menu", "M4 7h16M4 12h16M4 17h16") }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Minus by lazy { icon("Minus", "M5 12h14") }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6L6 18") }
    val Share by lazy { icon("Share", "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7", "M16 6l-4-4-4 4", "M12 2v13") }
    val Import by lazy { icon("Import", "M12 4v11M7 10l5 5 5-5M5 20h14") }
    val Export by lazy { icon("Export", "M12 20V9M7 14l5-5 5 5M5 4h14") }
    val Compare by lazy { icon("Compare", "M9 11l3 3 7-7", "M20 12v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h8") }
    val ListLines by lazy { icon("ListLines", "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01") }
    /** Círculo con una raya: sin consumo. */
    val Zero by lazy { icon("Zero", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M8 12h8") }
    /** Ojo: lecturas sin leer. */
    val Download by lazy { icon("Download", "M12 3v12", "M7 10l5 5 5-5", "M4 20h16") }
    val Eye by lazy { icon("Eye", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z") }
    val Trend by lazy { icon("Trend", "M3 17l6-6 4 4 8-8", "M15 7h6v6") }
    val Clock by lazy { icon("Clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 7v5l3 2") }
    val Warning by lazy { icon("Warning", "M12 9v4M12 17h.01", "M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z") }

    // ---- Objetos ----
    val Play by lazy { icon("Play", "M8 5l11 7-11 7z") }
    val Stop by lazy { icon("Stop", "M7 7h10v10H7z") }
    val Box by lazy { icon("Box", "M3 7.5L12 3l9 4.5v9L12 21l-9-4.5z", "M3 7.5L12 12l9-4.5M12 12v9") }
    val Folder by lazy { icon("Folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    val Database by lazy {
        icon(
            "Database",
            "M4 6a8 3 0 1 0 16 0a8 3 0 1 0-16 0",
            "M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6",
            "M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"
        )
    }
    val Palette by lazy {
        icon(
            "Palette",
            "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 1.5-2s0-2.2 1.5-2.2H17a4 4 0 0 0 4-4C21 6.5 17 3 12 3z",
            "M7.5 11h.01M10 7h.01M15 7.5h.01"
        )
    }
    val Phone by lazy { icon("Phone", "M8.5 2.5h7A2.5 2.5 0 0 1 18 5v14a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 19V5a2.5 2.5 0 0 1 2.5-2.5z", "M12 18h.01") }
    val Vibrate by lazy { icon("Vibrate", "M10 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M4 8v8M20 8v8M1.5 10.5v3M22.5 10.5v3") }
    val Sun by lazy {
        icon(
            "Sun",
            "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
            "M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M19 5l-1.5 1.5M6.5 17.5L5 19"
        )
    }
    val Moon by lazy { icon("Moon", "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z") }
    val Gear by lazy {
        icon(
            "Gear",
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
            "M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.4-2.3.9a7 7 0 0 0-2-1.2L14 3h-4l-.6 2.6a7 7 0 0 0-2 1.2l-2.3-.9-2 3.4 2 1.5A7 7 0 0 0 5 12c0 .4 0 .8.1 1.2l-2 1.5 2 3.4 2.3-.9a7 7 0 0 0 2 1.2L10 21h4l.6-2.6a7 7 0 0 0 2-1.2l2.3.9 2-3.4-2-1.5c.1-.4.1-.8.1-1.2z"
        )
    }

    // ---- Mapa ----
    val Locate by lazy {
        icon(
            "Locate",
            "M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z",
            "M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2z",
            "M12 1v3M12 20v3M1 12h3M20 12h3"
        )
    }
    val Layers by lazy { icon("Layers", "M12 3l9 5-9 5-9-5z", "M3 13l9 5 9-5") }
    val FitAll by lazy { icon("FitAll", "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5") }
    val North by lazy { icon("North", "M12 2l4 10-4-2-4 2z", "M12 22v-8") }
}
