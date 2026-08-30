package com.auracam.location

actual class PlatformLocationProvider : LocationProvider {
    override fun lastKnownLocation(): GeoLocation? = null
}
