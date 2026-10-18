package com.lecturasgas.beta.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Brújula del celular: hacia dónde apunta el celular (0° = norte verdadero), aunque estés parado.
 * Sirve para girar el mapa cuando no te mueves. Se enciende solo mientras el mapa está visible.
 * Con el celular acostado usa la parte de arriba del celular; con el celular parado (como al
 * mirar el mapa caminando), la parte de atrás, como hacen Google Maps y Waze.
 */
class CompassSensor(context: Context) {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _heading = MutableStateFlow<Double?>(null)
    /** Rumbo en grados (0–360), o null si el celular no tiene brújula o aún no mide. */
    val heading: StateFlow<Double?> = _heading.asStateFlow()

    val available: Boolean get() = sensor != null

    private var declination = 0f
    private var lastPublish = 0L
    private var smooth: Double? = null
    private var running = false

    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            SensorManager.getOrientation(rotation, orientation)
            val pitch = Math.toDegrees(orientation[1].toDouble())
            val azimuthRad = if (abs(pitch) > UPRIGHT_PITCH) {
                // Celular parado: el rumbo es hacia donde mira la cámara trasera.
                SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                val o = FloatArray(3)
                SensorManager.getOrientation(remapped, o)
                o[0]
            } else orientation[0]
            val deg = Heading.normalize(Math.toDegrees(azimuthRad.toDouble()) + declination)
            val prev = smooth
            val s = if (prev == null) deg else Heading.normalize(prev + Heading.delta(prev, deg) * ALPHA)
            smooth = s
            val now = event.timestamp / 1_000_000L
            if (now - lastPublish >= PUBLISH_EVERY_MS) {
                val shown = _heading.value
                if (shown == null || abs(Heading.delta(shown, s)) >= MIN_CHANGE) {
                    _heading.value = s
                    lastPublish = now
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** Corrige del norte magnético al norte verdadero (en Colombia son ~6° de diferencia). */
    fun setLocation(lat: Double, lon: Double) {
        declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
    }

    fun start() {
        val s = sensor ?: return
        if (running) return
        running = true
        manager.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        manager.unregisterListener(listener)
        smooth = null
        _heading.value = null
    }

    private companion object {
        const val UPRIGHT_PITCH = 50.0
        const val ALPHA = 0.18
        const val PUBLISH_EVERY_MS = 150L
        const val MIN_CHANGE = 2.0
    }
}
