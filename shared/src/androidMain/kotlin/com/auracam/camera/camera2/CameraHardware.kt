package com.auracam.camera.camera2

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log
import android.util.Size

internal const val CAMERA2_TAG = "AuraCam2"

/**
 * Static, read-only view of the device's camera hardware.
 *
 * Everything here is derived once from [CameraCharacteristics] so the session layer never has to
 * re-query the [CameraManager] while a capture session is live.
 */
internal class CameraHardware(context: Context) {

    private val manager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    data class Lens(
        val cameraId: String,
        val isFront: Boolean,
        val focalLength: Float,
        /** Focal length relative to the widest back lens, i.e. the "1x / 2x / 5x" badge value. */
        val baseZoom: Float,
        val maxDigitalZoom: Float,
        val rawSupported: Boolean,
        val characteristics: CameraCharacteristics
    ) {
        val sensorOrientation: Int
            get() = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val hasFlashUnit: Boolean
            get() = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    val lenses: List<Lens>
    val backLenses: List<Lens>
    val frontLens: Lens?

    /**
     * A back/front camera-id pair the HAL guarantees can stream at the same time, or null when the
     * device does not advertise any concurrent combination.
     */
    val concurrentPair: Pair<String, String>?

    init {
        val discovered = runCatching { manager.cameraIdList }.getOrDefault(emptyArray())
            .mapNotNull { id ->
                runCatching {
                    val chars = manager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: return@runCatching null
                    // Skip anything that cannot serve a normal preview (depth-only helpers etc).
                    val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        ?: return@runCatching null
                    if (!caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                        return@runCatching null
                    }
                    val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        ?.minOrNull() ?: return@runCatching null
                    Triple(id, chars, facing to focal)
                }.getOrNull()
            }

        val backRaw = discovered.filter { it.third.first == CameraCharacteristics.LENS_FACING_BACK }
        val reference = backRaw.maxOfOrNull { it.third.second }
            ?: backRaw.firstOrNull()?.third?.second

        lenses = discovered.map { (id, chars, facingFocal) ->
            val (facing, focal) = facingFocal
            val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT
            Lens(
                cameraId = id,
                isFront = isFront,
                focalLength = focal,
                baseZoom = if (isFront || reference == null || reference <= 0f) 1.0f else focal / reference,
                maxDigitalZoom = chars.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
                ) ?: 1.0f,
                rawSupported = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true,
                characteristics = chars
            )
        }

        backLenses = lenses.filter { !it.isFront }.sortedBy { it.baseZoom }
        frontLens = lenses.firstOrNull { it.isFront }

        concurrentPair = resolveConcurrentPair()

        Log.i(
            CAMERA2_TAG,
            "Cameras: ${lenses.map { "${it.cameraId}${if (it.isFront) "F" else "B"}@${it.baseZoom}x" }}, " +
                "concurrentPair=$concurrentPair"
        )
    }

    private fun resolveConcurrentPair(): Pair<String, String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val sets = runCatching { manager.concurrentCameraIds }.getOrNull().orEmpty()
        for (set in sets) {
            val back = set.firstOrNull { id -> lenses.any { it.cameraId == id && !it.isFront } }
            val front = set.firstOrNull { id -> lenses.any { it.cameraId == id && it.isFront } }
            if (back != null && front != null) return back to front
        }
        return null
    }

    fun lensById(cameraId: String): Lens? = lenses.firstOrNull { it.cameraId == cameraId }

    /** The back lens whose optical zoom best matches [zoom], falling back to the widest. */
    fun backLensFor(zoom: Float): Lens? =
        backLenses.lastOrNull { it.baseZoom <= zoom + 0.05f } ?: backLenses.firstOrNull()

    fun zoomPresets(): List<Float> {
        val main = backLenses.lastOrNull { it.baseZoom >= 0.95f } ?: backLenses.lastOrNull()
        ?: return listOf(1.0f)
        val maxZoom = main.baseZoom * main.maxDigitalZoom
        val presets = mutableListOf<Float>()
        if (backLenses.any { it.baseZoom < 0.75f }) presets += 0.5f
        presets += 1.0f
        for (step in listOf(2.0f, 5.0f, 10.0f)) if (step <= maxZoom + 0.01f) presets += step
        return presets.distinct()
    }

    fun maxZoomFor(lens: Lens): Float = lens.baseZoom * lens.maxDigitalZoom

    /**
     * Largest supported output size for [format] that is no larger than [cap], preferring the
     * closest match to the requested aspect ratio.
     */
    fun bestSize(lens: Lens, format: Int, cap: Size, targetAspect: Float): Size? {
        val map = lens.characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        ) ?: return null
        val candidates = map.getOutputSizes(format)?.toList().orEmpty()
        if (candidates.isEmpty()) return null
        val capArea = cap.width.toLong() * cap.height
        return candidates
            .filter { it.width.toLong() * it.height <= capArea }
            .minByOrNull { size ->
                val aspect = size.width.toFloat() / size.height
                val aspectPenalty = kotlin.math.abs(aspect - targetAspect) * 4f
                val areaPenalty =
                    1f - (size.width.toLong() * size.height).toFloat() / capArea.toFloat()
                aspectPenalty + areaPenalty
            } ?: candidates.minByOrNull { it.width.toLong() * it.height }
    }

    fun bestPreviewSize(lens: Lens, cap: Size, targetAspect: Float): Size =
        bestSize(lens, ImageFormat.PRIVATE, cap, targetAspect)
            ?: bestSize(lens, ImageFormat.YUV_420_888, cap, targetAspect)
            ?: Size(1280, 720)
}
