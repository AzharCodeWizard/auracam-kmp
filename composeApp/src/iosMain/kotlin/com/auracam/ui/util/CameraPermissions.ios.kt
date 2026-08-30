package com.auracam.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState = remember { IosPermissionState() }

private class IosPermissionState : CameraPermissionState {
    private val locationManager = CLLocationManager()

    override val cameraGranted: Boolean
        get() = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
            AVAuthorizationStatusAuthorized

    override val microphoneGranted: Boolean
        get() = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) ==
            AVAuthorizationStatusAuthorized

    override val locationGranted: Boolean
        get() = CLLocationManager.authorizationStatus() == kCLAuthorizationStatusAuthorizedWhenInUse ||
            CLLocationManager.authorizationStatus() == kCLAuthorizationStatusAuthorizedAlways

    override val hasRequested: Boolean
        get() = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) != 0L

    override val shouldShowRationale = false

    override fun request() {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { }
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { }
    }

    override fun requestLocation() {
        locationManager.requestWhenInUseAuthorization()
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}
