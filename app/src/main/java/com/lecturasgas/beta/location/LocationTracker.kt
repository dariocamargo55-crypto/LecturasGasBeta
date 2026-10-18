package com.lecturasgas.beta.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.util.Geo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fuente ÚNICA de ubicación para toda la app (mapa, lecturas y entrega).
 *
 * Cómo logra más precisión (igual que Google Maps / Waze):
 *  1. Pide la ubicación de Google (Fused) cuando el celular la tiene, y el GPS normal
 *     en paralelo como respaldo. Funciona también SIN Google Play Services.
 *  2. Las ubicaciones por antenas (poco precisas) solo se usan mientras llega el primer GPS.
 *  3. Cada lectura pasa por un filtro de Kalman (GpsSmoother): el punto azul no salta
 *     y los saltos imposibles se descartan.
 *  4. Guarda las lecturas recientes para poder PROMEDIARLAS al registrar un medidor
 *     (captureBest): mientras escribes la lectura, la app ya está midiendo el punto.
 *
 * Lleva un contador de "usuarios": la pantalla y el servicio de entrega lo piden y,
 * cuando nadie lo necesita, se apaga el GPS para ahorrar batería.
 */
class LocationTracker(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _fix = MutableStateFlow<GeoFix?>(null)
    val fix: StateFlow<GeoFix?> = _fix.asStateFlow()

    private val _quality = MutableStateFlow(GpsQuality.NONE)
    /** Semáforo del GPS (verde = listo para guardar el punto de un medidor). */
    val quality: StateFlow<GpsQuality> = _quality.asStateFlow()

    private val _gpsEnabled = MutableStateFlow(true)
    val gpsEnabled: StateFlow<Boolean> = _gpsEnabled.asStateFlow()

    private var users = 0
    private var running = false

    private val smoother = GpsSmoother()
    private val recent = ArrayDeque<GeoFix>()          // lecturas crudas aceptadas (últimos 30 s)
    private var lastGoodMs = 0L                        // última vez que llegó un GPS/Google bueno
    private var fused: FusedSource? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handle(location, cached = false)
        }

        // IMPORTANTE: en Android 10 o menor estos métodos NO tienen implementación
        // por defecto. Si no se sobrescriben, la app se cierra (AbstractMethodError).
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) _gpsEnabled.value = true
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) _gpsEnabled.value = false
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun acquire() {
        users++
        if (!running) start()
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) stop()
    }

    /** Llamar después de que el usuario concede el permiso. */
    @Synchronized
    fun restart() {
        if (users > 0) {
            stop()
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (!hasPermission()) return
        _gpsEnabled.value = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(true)
        smoother.reset()
        synchronized(recent) { recent.clear() }
        lastGoodMs = 0L
        _quality.value = GpsQuality.NONE

        // Arranque rápido con la última ubicación conocida (solo para mostrar algo ya).
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching { manager.getLastKnownLocation(p) }.getOrNull()?.let { last ->
                if (System.currentTimeMillis() - last.time < 2 * 60_000L) handle(last, cached = true)
            }
        }

        // GPS del sistema, cada segundo y SIN distancia mínima (así sigue midiendo estando quieto).
        runCatching {
            if (manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            }
        }
        // Antenas: solo ayudan mientras llega el primer GPS (después se ignoran).
        runCatching {
            if (manager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3_000L, 0f, listener, Looper.getMainLooper())
            }
        }
        // Ubicación de Google (la de Google Maps), si el celular la tiene.
        fused = FusedSource.create(context)?.also { source ->
            source.start(
                onLocation = { handle(it, cached = false) },
                onFail = { /* sin problema: queda el GPS del sistema */ }
            )
        }
        running = true
    }

    private fun stop() {
        runCatching { manager.removeUpdates(listener) }
        fused?.stop()
        fused = null
        smoother.reset()
        running = false
    }

    @Synchronized
    private fun handle(location: Location, cached: Boolean) {
        val isNetwork = location.provider == LocationManager.NETWORK_PROVIDER
        val accuracy = if (location.hasAccuracy()) location.accuracy else 80f
        if (accuracy > MAX_USEFUL_ACCURACY) return

        // Lecturas viejas no sirven (excepto la de arranque).
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        if (!cached && ageMs > MAX_AGE_MS) return

        // Las antenas solo valen mientras no haya GPS/Google reciente.
        val nowMs = SystemClock.elapsedRealtime()
        if (isNetwork && nowMs - lastGoodMs < 30_000L) return

        val speed = if (location.hasSpeed()) location.speed else null
        val timeNs = if (cached) SystemClock.elapsedRealtimeNanos() else location.elapsedRealtimeNanos
        val estimate = smoother.update(location.latitude, location.longitude, accuracy, speed, timeNs) ?: return

        if (!isNetwork && !cached && accuracy <= MAX_SAMPLE_ACCURACY) {
            lastGoodMs = nowMs
            val now = System.currentTimeMillis()
            synchronized(recent) {
                recent.addLast(GeoFix(location.latitude, location.longitude, accuracy, now, speed, null, "crudo"))
                while (recent.isNotEmpty() && now - recent.first().time > BUFFER_MS) recent.removeFirst()
            }
        }

        _fix.value = GeoFix(
            lat = estimate.lat,
            lon = estimate.lon,
            accuracy = estimate.accuracy,
            time = System.currentTimeMillis(),
            speed = speed,
            bearing = if (location.hasBearing()) location.bearing else null,
            provider = if (isNetwork) "network" else "gps"
        )
        val samples = synchronized(recent) { recent.toList() }
        _quality.value = GpsQualityRules.assess(_fix.value, samples, System.currentTimeMillis())
    }

    /** true si Android dio la ubicación PRECISA (no solo la aproximada). */
    fun hasPrecisePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Promedio de las lecturas de los últimos segundos (solo las que quedaron cerca de donde estás ahora). */
    private fun averageRecent(): Capture? {
        val samples = synchronized(recent) { recent.toList() }
        if (samples.isEmpty()) return null
        val latest = samples.last()
        val now = System.currentTimeMillis()
        if (now - latest.time > MAX_SAMPLE_AGE_MS) return null          // se perdió la señal
        val window = samples.filter {
            now - it.time <= CAPTURE_WINDOW_MS &&
                    Geo.distanceMeters(it.lat, it.lon, latest.lat, latest.lon) <= CAPTURE_RADIUS_M
        }
        return PositionAverager.average(window)
    }

    /**
     * Mejor ubicación posible AHORA, promediando las últimas lecturas. Como la app lleva
     * midiendo desde que abriste la tarjeta del medidor, casi siempre ya hay datos y responde
     * al instante; si faltan, espera hasta [maxWaitMs] por más lecturas.
     *
     * Devuelve la mejor que haya (quien llama decide si la precisión alcanza), o null sin señal.
     */
    suspend fun captureBest(
        maxWaitMs: Long = 5_000L,
        goodAccuracy: Float = 10f,
        minSamples: Int = 4
    ): Capture? {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (true) {
            val c = averageRecent()
            if (c != null && c.samples >= minSamples && (c.fix.accuracy ?: 999f) <= goodAccuracy) return c
            if (SystemClock.elapsedRealtime() >= deadline) return c
            delay(500L)
        }
    }

    private companion object {
        const val MAX_USEFUL_ACCURACY = 100f      // peor que esto ni se muestra
        const val MAX_SAMPLE_ACCURACY = 30f       // para promediar un medidor
        const val MAX_AGE_MS = 8_000L
        const val BUFFER_MS = 30_000L
        const val CAPTURE_WINDOW_MS = 20_000L
        const val CAPTURE_RADIUS_M = 25.0
        const val MAX_SAMPLE_AGE_MS = 6_000L
    }
}
