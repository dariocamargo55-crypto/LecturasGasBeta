package com.lecturasgas.beta.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/** true cuando la app va en «modo ligero»: menos animaciones y menos pantallas preparadas a la vez. */
val LocalLite = staticCompositionLocalOf { false }

/** Decisiones de rendimiento y tamaño según el celular. */
object Perf {
    /** Celular viejo o con poca memoria: Android 9 o menor, o equipo marcado como de poca RAM. */
    fun isOldDevice(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return Build.VERSION.SDK_INT < 29 ||
            am?.isLowRamDevice == true ||
            (am?.memoryClass ?: 256) < 192
    }

    /** Cuánto se encoge toda la interfaz. Automático: pantallas bajas (16:9) se achican un poco. */
    fun densityFactor(mode: String, screenHeightDp: Int): Float = when (mode) {
        "NORMAL" -> 1f
        "COMPACT" -> 0.92f
        "TINY" -> 0.84f
        else -> if (screenHeightDp < 700) 0.92f else 1f
    }
}
