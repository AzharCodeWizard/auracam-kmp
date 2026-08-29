package com.auracam.camera.domain

actual class PlatformCameraEngine : BaseCameraEngine() {
    // iOS AVFoundation & Metal Shaders bridge hooks
    fun bindSession() {
        // Native AVCaptureSession binding
    }
}
