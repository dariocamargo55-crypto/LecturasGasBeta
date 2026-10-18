package com.lecturasgas.beta

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.lecturasgas.beta.data.backup.BackupManager
import com.lecturasgas.beta.data.db.LocalStore
import com.lecturasgas.beta.data.repository.LecturasRepository
import com.lecturasgas.beta.data.repository.UiPrefs
import com.lecturasgas.beta.location.LocationTracker

/**
 * Punto de arranque de la app. Crea UNA sola vez las piezas compartidas
 * (base de datos, GPS, repositorio) para que todas las pantallas usen las mismas.
 */
class LecturasApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Respaldo automático: cada vez que cambian lecturas, GPS o entregas.
        container.backup.startAuto(appScope, container.repository)
    }
}

class AppContainer(app: Application) {
    val store = LocalStore(app)
    val tracker = LocationTracker(app)
    val prefs = UiPrefs(app)
    val repository = LecturasRepository(app, store, prefs)
    val backup = BackupManager(app, store, prefs)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LecturasApp).container
