package com.auracam.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState = remember { AlwaysGranted }

private object AlwaysGranted : CameraPermissionState {
    override val cameraGranted = true
    override val microphoneGranted = true
    override val locationGranted = true
    override val hasRequested = true
    override val shouldShowRationale = false
    override fun request() = Unit
    override fun requestLocation() = Unit
    override fun openAppSettings() = Unit
}
