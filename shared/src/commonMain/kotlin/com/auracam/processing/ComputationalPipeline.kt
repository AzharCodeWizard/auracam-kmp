package com.auracam.processing

import com.auracam.camera.domain.*
import com.auracam.location.GeoLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

object ComputationalPipeline {

    fun processCapture(
        mode: CameraMode,
        lens: LensFacing,
        zoom: Float,
        proSettings: ProSettings,
        colorProfile: ColorProfile,
        captureFormat: CaptureFormat,
        watermarkEnabled: Boolean,
        ultraHdr: Boolean
    ): Flow<CaptureProgress> = flow {
        emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.15f, "Capturing frame..."))
        delay(120)

        when (mode) {
            CameraMode.NIGHT_SIGHT -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.35f, "Preparing low-light capture..."))
                delay(200)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.65f, "Processing..."))
                delay(250)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.85f, "Finishing..."))
                delay(150)
            }
            CameraMode.ASTRO -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.25f, "Preparing long exposure..."))
                delay(250)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.60f, "Processing..."))
                delay(250)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.90f, "Finishing..."))
                delay(200)
            }
            CameraMode.PORTRAIT -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.30f, "Preparing capture..."))
                delay(150)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.60f, "Processing..."))
                delay(180)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.85f, "Finishing..."))
                delay(120)
            }
            CameraMode.PRO -> {
                val isoText = if (proSettings.isIsoAuto) "ISO 100" else "ISO ${proSettings.iso}"
                val shutterText = proSettings.formatShutterSpeed()
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.50f, "Manual sensor readout ($isoText, $shutterText)..."))
                delay(150)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.80f, "Finishing..."))
                delay(120)
            }
            CameraMode.LONG_EXPOSURE -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.30f, "Preparing long exposure..."))
                delay(200)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.70f, "Processing..."))
                delay(200)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.90f, "Finishing..."))
                delay(150)
            }
            else -> {
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.50f, "Processing..."))
                delay(100)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.80f, "Finishing..."))
                delay(100)
            }
        }

        emit(CaptureProgress(CaptureState.SAVING, 0.95f, "Encoding ${captureFormat.label} with EXIF metadata..."))
        delay(80)

        emit(CaptureProgress(CaptureState.COMPLETE, 1.0f, "Saved to Gallery"))
    }

    fun generateExif(
        mode: CameraMode,
        lens: LensFacing,
        zoom: Float,
        proSettings: ProSettings,
        captureFormat: CaptureFormat,
        ultraHdr: Boolean,
        capturedAtEpochMillis: Long,
        location: GeoLocation? = null
    ): ExifInfo {
        val focalLength = when (lens) {
            LensFacing.BACK_ULTRA_WIDE -> "13mm (f/2.2)"
            LensFacing.BACK_WIDE -> "24mm (f/1.68)"
            LensFacing.BACK_TELEPHOTO -> "48mm (f/1.68)"
            LensFacing.BACK_SUPER_TELE -> "120mm (f/2.8)"
            LensFacing.FRONT -> "20mm (f/2.2)"
        }

        val effectiveIso = if (proSettings.isIsoAuto) {
            when (mode) {
                CameraMode.NIGHT_SIGHT, CameraMode.ASTRO -> 1600
                else -> 100
            }
        } else proSettings.iso

        val effectiveShutter = if (proSettings.isShutterAuto) {
            when (mode) {
                CameraMode.NIGHT_SIGHT -> "1/4s"
                CameraMode.ASTRO -> "16s"
                CameraMode.LONG_EXPOSURE -> "2.5s"
                else -> "1/250s"
            }
        } else proSettings.formatShutterSpeed()

        val formatDesc = if (ultraHdr) "Ultra HDR (${captureFormat.label})" else captureFormat.label
        val resolutionDesc = when (mode) {
            CameraMode.PRO -> "50 MP (8192 × 6144)"
            CameraMode.ASTRO -> "50 MP (8192 × 6144)"
            else -> "12.5 MP Quad-Binned (4080 × 3072)"
        }

        return ExifInfo(
            deviceModel = "Google Pixel Pro (AuraCam Engine)",
            lensFocalLength = focalLength,
            iso = effectiveIso,
            shutterSpeed = effectiveShutter,
            aperture = if (lens == LensFacing.BACK_ULTRA_WIDE || lens == LensFacing.FRONT) "f/2.2" else "f/1.68",
            exposureBias = "${if (proSettings.evBias >= 0) "+" else ""}${proSettings.evBias} EV",
            whiteBalance = proSettings.formatWb(),
            format = formatDesc,
            resolution = resolutionDesc,
            timestamp = formatTimestamp(capturedAtEpochMillis),
            location = location?.let { formatLocation(it) }
        )
    }

    fun formatTimestamp(epochMillis: Long): String {
        val local = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return buildString {
            append(local.year.toString().padStart(4, '0'))
            append('-')
            append(local.monthNumber.toString().padStart(2, '0'))
            append('-')
            append(local.dayOfMonth.toString().padStart(2, '0'))
            append(' ')
            append(local.hour.toString().padStart(2, '0'))
            append(':')
            append(local.minute.toString().padStart(2, '0'))
            append(':')
            append(local.second.toString().padStart(2, '0'))
        }
    }

    fun formatLocation(location: GeoLocation): String {
        val lat = formatCoordinate(location.latitude, positive = "N", negative = "S")
        val lon = formatCoordinate(location.longitude, positive = "E", negative = "W")
        return "$lat, $lon"
    }

    private fun formatCoordinate(value: Double, positive: String, negative: String): String {
        val hemisphere = if (value >= 0) positive else negative
        val magnitude = abs(value)
        val rounded = (magnitude * 10000).roundToInt() / 10000.0
        return "$rounded° $hemisphere"
    }

    fun formatPixelWatermark(exif: ExifInfo): String {
        return "Shot on Pixel | ${exif.lensFocalLength} ${exif.shutterSpeed} ISO ${exif.iso}"
    }
}
