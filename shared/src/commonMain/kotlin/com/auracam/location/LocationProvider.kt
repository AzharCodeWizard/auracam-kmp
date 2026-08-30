package com.auracam.location

import kotlinx.serialization.Serializable

@Serializable
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null
)

interface LocationProvider {
    fun lastKnownLocation(): GeoLocation?
}

expect class PlatformLocationProvider() : LocationProvider
