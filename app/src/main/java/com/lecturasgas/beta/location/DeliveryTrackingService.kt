package com.lecturasgas.beta.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lecturasgas.beta.MainActivity
import com.lecturasgas.beta.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que registra el recorrido (estilo Strava) mientras
 * la jornada de entrega está activa, aunque la pantalla se apague.
 *
 * Si Android cierra la app, el sistema lo vuelve a iniciar (START_STICKY) y
 * continúa la misma jornada porque está guardada en la base de datos.
 */
class DeliveryTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var acquired = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val container = applicationContext.appContainer
        if (!container.tracker.hasPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else 0
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (collectJob == null) {
            container.tracker.acquire()
            acquired = true
            collectJob = scope.launch {
                // Si el sistema reinició el servicio y ya no hay jornada, se detiene.
                if (!container.repository.ensureActiveSessionLoaded()) {
                    stopSelf()
                    return@launch
                }
                container.tracker.fix.collect { fix ->
                    if (fix != null) container.repository.onTrackFix(fix)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jornada de entrega", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Lecturas Gas — jornada activa")
            .setContentText("Registrando tu recorrido de entrega")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        if (acquired) {
            applicationContext.appContainer.tracker.release()
            acquired = false
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "delivery_tracking"
        private const val NOTIFICATION_ID = 2405
        private const val ACTION_STOP = "com.lecturasgas.beta.STOP_TRACKING"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DeliveryTrackingService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, DeliveryTrackingService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
