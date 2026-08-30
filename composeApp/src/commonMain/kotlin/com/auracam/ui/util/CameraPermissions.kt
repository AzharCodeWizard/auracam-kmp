package com.auracam.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface CameraPermissionState {
    val cameraGranted: Boolean
    val microphoneGranted: Boolean
    val locationGranted: Boolean
    val hasRequested: Boolean
    val shouldShowRationale: Boolean

    fun request()
    fun requestLocation()
    fun openAppSettings()
}

val CameraPermissionState.isCameraPermanentlyDenied: Boolean
    get() = hasRequested && !cameraGranted && !shouldShowRationale

@Composable
expect fun rememberCameraPermissionState(): CameraPermissionState
