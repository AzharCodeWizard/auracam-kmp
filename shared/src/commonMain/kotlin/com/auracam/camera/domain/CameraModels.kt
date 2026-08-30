package com.auracam.camera.domain

import kotlinx.serialization.Serializable

@Serializable
data class ProSettings(
    val iso: Int = 100,
    val isIsoAuto: Boolean = true,
    val shutterSpeedDenominator: Long = 125, // 1/125s
    val isShutterAuto: Boolean = true,
    val evBias: Float = 0.0f, // -3.0 to +3.0 EV
    val kelvinWb: Int = 5500, // 2000K to 10000K
    val isWbAuto: Boolean = true,
    val manualFocusDistance: Float = 0.0f, // 0.0 (infinity) to 1.0 (macro)
    val isFocusAuto: Boolean = true,
    val highlightBias: Float = 0.0f, // Dual exposure slider (highlights) -1.0 to 1.0
    val shadowBias: Float = 0.0f, // Dual exposure slider (shadows) -1.0 to 1.0
    val focusPeakingEnabled: Boolean = false,
    val zebraClippingEnabled: Boolean = false
) {
    fun formatShutterSpeed(): String {
        return if (isShutterAuto) {
            "Auto"
        } else if (shutterSpeedDenominator >= 1) {
            "1/${shutterSpeedDenominator}s"
        } else {
            val seconds = (1.0f / shutterSpeedDenominator).toInt()
            "${seconds}s"
        }
    }

    fun formatIso(): String {
        return if (isIsoAuto) "Auto" else "ISO $iso"
    }

    fun formatWb(): String {
        return if (isWbAuto) "Auto" else "${kelvinWb}K"
    }

    fun formatFocus(): String {
        return if (isFocusAuto) "Auto" else if (manualFocusDistance > 0.8f) "Macro 🌷" else if (manualFocusDistance < 0.2f) "Infinity ⛰️" else "${(manualFocusDistance * 100).toInt()}%"
    }
}

@Serializable
data class HistogramData(
    val redBins: List<Int> = List(32) { 0 },
    val greenBins: List<Int> = List(32) { 0 },
    val blueBins: List<Int> = List(32) { 0 },
    val luminanceBins: List<Int> = List(32) { 0 }
)

class ExposureMask(
    val width: Int = 0,
    val height: Int = 0,
    val peaking: ByteArray = ByteArray(0),
    val zebra: ByteArray = ByteArray(0)
) {
    val isEmpty: Boolean get() = width == 0 || height == 0
}

@Serializable
data class HorizonLeveler(
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val isLevel: Boolean = false
)

@Serializable
data class FocusPoint(
    val x: Float, // Normalized 0.0 to 1.0
    val y: Float, // Normalized 0.0 to 1.0
    val timestamp: Long = 0L
)

@Serializable
data class ExifInfo(
    val deviceModel: String = "Google Pixel (AuraCam)",
    val lensFocalLength: String = "24mm (f/1.68)",
    val iso: Int = 100,
    val shutterSpeed: String = "1/120s",
    val aperture: String = "f/1.68",
    val exposureBias: String = "+0.0 EV",
    val whiteBalance: String = "5500K",
    val format: String = "Ultra HDR DNG",
    val resolution: String = "50 MP (8192 × 6144)",
    val timestamp: String = "",
    val location: String? = null
)

@Serializable
data class CapturedMedia(
    val id: String,
    val uri: String,
    val fileName: String,
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val format: CaptureFormat,
    val mode: CameraMode,
    val exif: ExifInfo,
    val simulatedPreviewSeed: Int = 0
)

enum class CaptureState {
    IDLE,
    ALIGNING_FRAMES,
    EXPOSURE_STACKING,
    DENOISING_REALTONE,
    SAVING,
    COMPLETE
}

@Serializable
data class CaptureProgress(
    val state: CaptureState = CaptureState.IDLE,
    val progress: Float = 0f,
    val message: String = ""
)
