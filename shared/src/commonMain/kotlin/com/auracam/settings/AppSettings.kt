package com.auracam.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val rawCaptureEnabled: Boolean = false,
    val geotaggingEnabled: Boolean = false,
    val shutterSoundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val framingHintsEnabled: Boolean = true,
    val videoStabilizationEnabled: Boolean = true,
    val watermarkEnabled: Boolean = false
)
