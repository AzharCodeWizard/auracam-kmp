package com.auracam.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "AuraCamLocation"
private const val MAX_FIX_AGE_MILLIS = 5 * 60 * 1000L

actual class PlatformLocationProvider : LocationProvider {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    override fun lastKnownLocation(): GeoLocation? {
        val context = appContext ?: return null
        if (!hasLocationPermission(context)) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val best = try {
            manager.allProviders
                .mapNotNull { provider ->
                    try {
                        manager.getLastKnownLocation(provider)
                    } catch (e: SecurityException) {
                        null
                    }
                }
                .filter { System.currentTimeMillis() - it.time <= MAX_FIX_AGE_MILLIS }
                .minByOrNull { it.accuracy }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read last known location", e)
            null
        } ?: return null

        return best.toGeoLocation()
    }

    private fun Location.toGeoLocation() = GeoLocation(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null
    )

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}
