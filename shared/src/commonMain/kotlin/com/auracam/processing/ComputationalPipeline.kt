package com.auracam.processing

import com.auracam.camera.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object ComputationalPipeline {

    /**
     * Executes the computational capture pipeline based on mode and pro settings.
     * Yields progressive capture states (Alignment -> Fusion -> Real Tone Denoise -> EXIF & Watermark).
     */
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
        emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.15f, "Capturing 15 Zero-Shutter-Lag raw frames..."))
        delay(120)

        when (mode) {
            CameraMode.NIGHT_SIGHT -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.35f, "Aligning multi-exposure low-light burst..."))
                delay(200)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.65f, "Stacking frames & reducing sensor noise..."))
                delay(250)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.85f, "Pixel Real Tone night synthesis..."))
                delay(150)
            }
            CameraMode.ASTRO -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.25f, "Celestial tracking & star centroid alignment..."))
                delay(250)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.60f, "Integrating 4-minute sub-exposures..."))
                delay(250)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.90f, "Dark-sky noise subtraction & nebula boost..."))
                delay(200)
            }
            CameraMode.PORTRAIT -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.30f, "Estimating neural depth map..."))
                delay(150)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.60f, "Synthesizing f/1.4 aperture bokeh..."))
                delay(180)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.85f, "Refining edge strands & skin tones..."))
                delay(120)
            }
            CameraMode.PRO -> {
                val isoText = if (proSettings.isIsoAuto) "ISO 100" else "ISO ${proSettings.iso}"
                val shutterText = proSettings.formatShutterSpeed()
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.50f, "Manual sensor readout ($isoText, $shutterText)..."))
                delay(150)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.80f, "Applying RAW Bayer demosaicing & 3D LUT..."))
                delay(120)
            }
            CameraMode.LONG_EXPOSURE -> {
                emit(CaptureProgress(CaptureState.ALIGNING_FRAMES, 0.30f, "Tracking motion vectors for light trails..."))
                delay(200)
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.70f, "Accumulating temporal light streaks..."))
                delay(200)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.90f, "Smoothing motion blur & stabilizing background..."))
                delay(150)
            }
            else -> {
                emit(CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.50f, "HDR+ bracketed exposure fusion..."))
                delay(100)
                emit(CaptureProgress(CaptureState.DENOISING_REALTONE, 0.80f, "Tone mapping & Pixel Real Tone processing..."))
                delay(100)
            }
        }

        emit(CaptureProgress(CaptureState.SAVING, 0.95f, "Encoding ${captureFormat.label} with EXIF metadata..."))
        delay(80)

        emit(CaptureProgress(CaptureState.COMPLETE, 1.0f, "Saved to Gallery"))
    }

    /**
     * Builds comprehensive EXIF and camera metadata.
     */
    fun generateExif(
        mode: CameraMode,
        lens: LensFacing,
        zoom: Float,
        proSettings: ProSettings,
        captureFormat: CaptureFormat,
        ultraHdr: Boolean
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
            timestamp = "2026-08-29 13:30:15",
            location = "Googleplex, Mountain View, CA (37.4220° N, 122.0841° W)"
        )
    }

    /**
     * Formats the Pixel-style Watermark string.
     * Example: "Shot on Pixel | 24mm f/1.68 1/250s ISO 100"
     */
    fun formatPixelWatermark(exif: ExifInfo): String {
        return "Shot on Pixel | ${exif.lensFocalLength} ${exif.shutterSpeed} ISO ${exif.iso}"
    }
}
