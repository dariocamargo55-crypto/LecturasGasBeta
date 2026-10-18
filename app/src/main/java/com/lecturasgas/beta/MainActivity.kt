package com.lecturasgas.beta

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lecturasgas.beta.ui.MainViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lecturasgas.beta.ui.LocalLite
import com.lecturasgas.beta.ui.Perf
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.ui.theme.ThemeMode
import com.lecturasgas.beta.ui.AppRoot
import com.lecturasgas.beta.ui.theme.LecturasTheme

/**
 * MainActivity ahora es mínima: solo arranca la interfaz.
 * Cada módulo vive en su propio paquete (readings, map, delivery).
 */
class MainActivity : ComponentActivity() {

    private val mainVm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pantalla de inicio: se queda hasta que la ruta carga (máximo 1,3 s) y sale con un zoom suave.
        val splash = installSplashScreen()
        val started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !mainVm.loaded.value && SystemClock.uptimeMillis() - started < 1300L
        }
        splash.setOnExitAnimationListener { provider ->
            val v = provider.view
            v.animate()
                .alpha(0f).scaleX(1.12f).scaleY(1.12f)
                .setDuration(380L)
                .withEndAction { provider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        setContent {
            val cfg by appContainer.prefs.settings.collectAsState()
            val mode = runCatching { ThemeMode.valueOf(cfg.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
            val ctx = LocalContext.current
            val lite = when (cfg.liteMode) {
                "ON" -> true
                "OFF" -> false
                else -> remember { Perf.isOldDevice(ctx) }
            }
            // Tamaño de la interfaz: en pantallas bajas (16:9) todo se achica un poco para que quepa igual que en un celular alto.
            val base = LocalDensity.current
            val heightDp = LocalConfiguration.current.screenHeightDp
            val factor = Perf.densityFactor(cfg.uiScale, heightDp)
            val density = remember(base.density, base.fontScale, factor) {
                Density(base.density * factor, minOf(base.fontScale, 1.3f))
            }
            CompositionLocalProvider(LocalDensity provides density, LocalLite provides lite) {
                LecturasTheme(mode) {
                    // Barras del sistema transparentes; iconos claros u oscuros según el tema.
                    val dark = AppColors.dark
                    DisposableEffect(dark) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                            navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                        )
                        onDispose { }
                    }
                    AppRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // El GPS se enciende mientras la app está visible (y durante la jornada, por el servicio).
        appContainer.tracker.acquire()
    }

    override fun onStop() {
        appContainer.tracker.release()
        super.onStop()
    }
}
