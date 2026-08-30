package com.auracam.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse

@OptIn(ExperimentalForeignApi::class)
actual class PlatformLocationProvider : LocationProvider {
    private val manager = CLLocationManager()

    override fun lastKnownLocation(): GeoLocation? {
        val status = CLLocationManager.authorizationStatus()
        if (status != kCLAuthorizationStatusAuthorizedWhenInUse &&
            status != kCLAuthorizationStatusAuthorizedAlways
        ) {
            return null
        }
        val location = manager.location ?: return null
        return location.coordinate.useContents {
            GeoLocation(latitude = latitude, longitude = longitude)
        }
    }
}
