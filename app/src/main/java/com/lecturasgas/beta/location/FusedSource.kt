package com.lecturasgas.beta.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Ubicación de Google ("Fused"): la misma que usan Google Maps y Waze.
 * Combina GPS + WiFi + antenas + sensores del celular, y por eso aguanta mucho mejor
 * entre edificios que el GPS solo.
 *
 * Si el celular no tiene Google Play Services, [create] devuelve null y la app sigue
 * funcionando con el GPS normal del sistema (LocationTracker).
 */
internal class FusedSource private constructor(
    private val client: FusedLocationProviderClient
) {
    private var onLocation: (Location) -> Unit = {}

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) onLocation(location)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(onLocation: (Location) -> Unit, onFail: () -> Unit) {
        this.onLocation = onLocation
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { onFail() }
    }

    fun stop() {
        runCatching { client.removeLocationUpdates(callback) }
    }

    companion object {
        fun create(context: Context): FusedSource? = runCatching {
            val ok = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                    ConnectionResult.SUCCESS
            if (ok) FusedSource(LocationServices.getFusedLocationProviderClient(context)) else null
        }.getOrNull()
    }
}
