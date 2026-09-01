package com.auracam.camera.camera2

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.os.Build
import android.util.Range
import com.auracam.camera.domain.FlashMode
import com.auracam.camera.domain.ProSettings
import kotlin.math.roundToInt

/**
 * Per-lens capability snapshot plus the translation from AuraCam's domain settings into
 * concrete [CaptureRequest] keys.
 *
 * This is the whole reason for moving off CameraX: every knob below is applied directly to the
 * request the HAL executes, on every frame, instead of being funnelled through use-case defaults.
 */
internal class CaptureRequestTuner(private val lens: CameraHardware.Lens) {

    private val chars = lens.characteristics

    val activeArray: Rect =
        chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 1, 1)

    val isoRange: Range<Int>? =
        chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

    val exposureTimeRange: Range<Long>? =
        chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

    val minFocusDistance: Float =
        chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

    val manualSensorSupported: Boolean =
        chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true

    val opticalStabilizationSupported: Boolean =
        chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

    val videoStabilizationSupported: Boolean =
        chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true

    val exposureCompensationRange: Range<Int> =
        chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0)

    val exposureCompensationStep: Float =
        chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f

    val maxAfRegions: Int =
        chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0

    val maxAeRegions: Int =
        chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0

    /** Highest face-detection mode the HAL offers; 0 means the device cannot track faces. */
    val faceDetectMode: Int = run {
        val modes = chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
            ?.toList().orEmpty()
        when {
            modes.contains(CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL) ->
                CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL
            modes.contains(CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE) ->
                CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE
            else -> CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF
        }
    }

    val supportsFaceTracking: Boolean
        get() = faceDetectMode != CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF

    val hardwareLevelName: String = when (
        chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
    ) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3 (Pro Master)"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL (Hardware)"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        else -> "LEGACY"
    }

    val sensorMegapixels: Float = run {
        val size = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        if (size != null) (size.width * size.height) / 1_000_000f else 12f
    }

    private val zoomRatioRange: Range<Float>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }

    /**
     * State the tuner needs that is not part of [ProSettings].
     */
    data class Frame(
        val pro: ProSettings,
        val flash: FlashMode,
        /** Requested zoom relative to this lens's own optical base (1.0 = no digital zoom). */
        val digitalZoom: Float,
        val videoStabilization: Boolean,
        val isVideo: Boolean,
        /** Normalized (0..1) metering focus point, or null for full-frame auto. */
        val meteringPoint: Pair<Float, Float>? = null,
        /** Normalized (0..1) tracked-subject box, which takes priority over [meteringPoint]. */
        val trackingBox: FloatArray? = null,
        val faceTracking: Boolean = true
    )

    fun applyTo(builder: CaptureRequest.Builder, frame: Frame) {
        val pro = frame.pro

        applyExposure(builder, pro)
        applyWhiteBalance(builder, pro)
        applyFocus(builder, pro, frame)
        applyZoom(builder, frame.digitalZoom)
        applyFlash(builder, frame)
        applyRegions(builder, frame)
        applyQualityPipeline(builder, pro)
        applyStabilization(builder, pro, frame)

        builder.set(
            CaptureRequest.STATISTICS_FACE_DETECT_MODE,
            if (frame.faceTracking) faceDetectMode
            else CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF
        )

        if (exposureCompensationStep > 0f) {
            val index = (pro.evBias / exposureCompensationStep).roundToInt()
                .coerceIn(exposureCompensationRange.lower, exposureCompensationRange.upper)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, index)
        }
    }

    private fun applyExposure(builder: CaptureRequest.Builder, pro: ProSettings) {
        val wantsManual = manualSensorSupported && (!pro.isIsoAuto || !pro.isShutterAuto)
        if (!wantsManual) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            return
        }
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
        isoRange?.let { range ->
            val iso = if (pro.isIsoAuto) range.lower else pro.iso
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso.coerceIn(range.lower, range.upper))
        }
        exposureTimeRange?.let { range ->
            val denominator = pro.shutterSpeedDenominator.coerceAtLeast(1L)
            val nanos = if (pro.isShutterAuto) range.lower else 1_000_000_000L / denominator
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, nanos.coerceIn(range.lower, range.upper))
        }
    }

    private fun applyWhiteBalance(builder: CaptureRequest.Builder, pro: ProSettings) {
        builder.set(
            CaptureRequest.CONTROL_AWB_MODE,
            if (pro.isWbAuto) CameraMetadata.CONTROL_AWB_MODE_AUTO else kelvinToAwbMode(pro.kelvinWb)
        )
    }

    private fun applyFocus(
        builder: CaptureRequest.Builder,
        pro: ProSettings,
        frame: Frame
    ) {
        if (!pro.isFocusAuto && minFocusDistance > 0f) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.set(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                (pro.manualFocusDistance * minFocusDistance).coerceIn(0f, minFocusDistance)
            )
            return
        }
        // Continuous video AF keeps a moving vlog subject sharp; picture AF is snappier for stills.
        builder.set(
            CaptureRequest.CONTROL_AF_MODE,
            if (frame.isVideo) CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            else CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
    }

    private fun applyZoom(builder: CaptureRequest.Builder, digitalZoom: Float) {
        val zoom = digitalZoom.coerceAtLeast(1.0f)
        val range = zoomRatioRange
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && range != null) {
            builder.set(
                CaptureRequest.CONTROL_ZOOM_RATIO,
                zoom.coerceIn(range.lower, range.upper)
            )
            return
        }
        val clamped = zoom.coerceIn(1.0f, lens.maxDigitalZoom.coerceAtLeast(1.0f))
        val cropW = (activeArray.width() / clamped).roundToInt()
        val cropH = (activeArray.height() / clamped).roundToInt()
        val left = activeArray.left + (activeArray.width() - cropW) / 2
        val top = activeArray.top + (activeArray.height() - cropH) / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + cropW, top + cropH))
    }

    private fun applyFlash(builder: CaptureRequest.Builder, frame: Frame) {
        if (!lens.hasFlashUnit) {
            builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            return
        }
        when (frame.flash) {
            FlashMode.TORCH -> {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            }
            FlashMode.ON -> {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                if (!frame.isVideo) {
                    builder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    )
                }
            }
            FlashMode.AUTO -> {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                if (!frame.isVideo) {
                    builder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                    )
                }
            }
            FlashMode.OFF -> builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
        }
    }

    /**
     * Point the hardware 3A at whatever the user (or the tracker) is interested in.
     * A tracked subject wins over a stale tap so the metering follows the face.
     */
    private fun applyRegions(builder: CaptureRequest.Builder, frame: Frame) {
        val box = frame.trackingBox
        val region: MeteringRectangle? = when {
            box != null && box.size == 4 -> meteringRect(box[0], box[1], box[2], box[3], 1000)
            frame.meteringPoint != null -> {
                val (x, y) = frame.meteringPoint
                val half = 0.075f
                meteringRect(x - half, y - half, x + half, y + half, 900)
            }
            else -> null
        }
        if (region == null) {
            if (maxAfRegions > 0) builder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
            if (maxAeRegions > 0) builder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
            return
        }
        val regions = arrayOf(region)
        if (maxAfRegions > 0) builder.set(CaptureRequest.CONTROL_AF_REGIONS, regions)
        if (maxAeRegions > 0) builder.set(CaptureRequest.CONTROL_AE_REGIONS, regions)
    }

    private fun meteringRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        weight: Int
    ): MeteringRectangle {
        val l = (activeArray.left + left.coerceIn(0f, 1f) * activeArray.width()).roundToInt()
        val t = (activeArray.top + top.coerceIn(0f, 1f) * activeArray.height()).roundToInt()
        val r = (activeArray.left + right.coerceIn(0f, 1f) * activeArray.width()).roundToInt()
        val b = (activeArray.top + bottom.coerceIn(0f, 1f) * activeArray.height()).roundToInt()
        return MeteringRectangle(
            l.coerceAtMost(r - 1),
            t.coerceAtMost(b - 1),
            (r - l).coerceAtLeast(1),
            (b - t).coerceAtLeast(1),
            weight.coerceIn(0, MeteringRectangle.METERING_WEIGHT_MAX)
        )
    }

    private fun applyQualityPipeline(builder: CaptureRequest.Builder, pro: ProSettings) {
        builder.set(
            CaptureRequest.NOISE_REDUCTION_MODE,
            if (pro.hardwareDenoiseQuality) CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            else CameraMetadata.NOISE_REDUCTION_MODE_FAST
        )
        builder.set(
            CaptureRequest.EDGE_MODE,
            if (pro.edgeSharpeningBoost) CameraMetadata.EDGE_MODE_HIGH_QUALITY
            else CameraMetadata.EDGE_MODE_FAST
        )
        builder.set(
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
            CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
        )
        builder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_HIGH_QUALITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.set(
                CaptureRequest.DISTORTION_CORRECTION_MODE,
                CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY
            )
        }
    }

    private fun applyStabilization(
        builder: CaptureRequest.Builder,
        pro: ProSettings,
        frame: Frame
    ) {
        if (opticalStabilizationSupported) {
            builder.set(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                if (pro.oisEnabled) CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                else CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
            )
        }
        if (videoStabilizationSupported) {
            builder.set(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (frame.videoStabilization) CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
        }
    }

    private fun kelvinToAwbMode(kelvin: Int): Int = when {
        kelvin <= 3000 -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
        kelvin <= 4200 -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
        kelvin <= 5200 -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
        kelvin <= 6500 -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
        else -> CameraMetadata.CONTROL_AWB_MODE_SHADE
    }
}
