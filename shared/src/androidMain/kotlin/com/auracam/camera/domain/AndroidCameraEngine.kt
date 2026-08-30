package com.auracam.camera.domain

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.database.Cursor
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.auracam.location.GeoLocation
import com.auracam.location.PlatformLocationProvider
import com.auracam.processing.ComputationalPipeline
import com.auracam.sensor.PlatformSensorLeveler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "AuraCamEngine"

actual class PlatformCameraEngine : BaseCameraEngine(simulateSensors = false) {
    private var context: Context? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val sensorLeveler = PlatformSensorLeveler()
    private val locationProvider = PlatformLocationProvider()
    private var orientationListener: OrientationEventListener? = null

    private val captureInFlight = AtomicBoolean(false)

    private val histogramRed = IntArray(BIN_COUNT)
    private val histogramGreen = IntArray(BIN_COUNT)
    private val histogramBlue = IntArray(BIN_COUNT)
    private val histogramLuma = IntArray(BIN_COUNT)
    private var histogramRowBuffer = ByteArray(0)
    private var lastHistogramEmitNanos = 0L
    private var lumaGrid = ByteArray(0)
    private var lumaGridWidth = 0
    private var lumaGridHeight = 0

    private var boundSignature: String? = null

    private data class LensOption(
        val cameraId: String,
        val selector: CameraSelector,
        val isFront: Boolean,
        val baseZoom: Float,
        val minRatio: Float,
        val maxRatio: Float,
        val rawSupported: Boolean
    )

    private var backLenses: List<LensOption> = emptyList()
    private var frontLens: LensOption? = null
    private var activeLens: LensOption? = null
    private var rawSupported: Boolean = false
    private var manualSensorSupported: Boolean = false
    private var deviceSupportsVideoStabilization: Boolean = false
    private var isoRange: Range<Int>? = null
    private var exposureTimeRange: Range<Long>? = null
    private var minFocusDistance: Float = 0f

    @Volatile
    private var released = false

    fun bindToLifecycle(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (released) return
        val appContext = context.applicationContext
        this.context = appContext
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView

        locationProvider.initialize(appContext)
        coroutineScope.launch { refreshGallery() }
        startSensorLeveler(appContext)
        startOrientationListener(appContext)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener({
            if (released) return@addListener
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                discoverLenses(provider)
                startCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider unavailable", e)
                _captureProgress.value = CaptureProgress(
                    CaptureState.IDLE, 0f, "Camera unavailable on this device"
                )
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun startSensorLeveler(context: Context) {
        sensorLeveler.initialize(context)
        sensorLeveler.start()
        coroutineScope.launch {
            sensorLeveler.horizonLeveler.collectLatest { _horizonLeveler.value = it }
        }
    }

    private fun startOrientationListener(context: Context) {
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when {
                    orientation >= 315 || orientation < 45 -> Surface.ROTATION_0
                    orientation < 135 -> Surface.ROTATION_270
                    orientation < 225 -> Surface.ROTATION_180
                    else -> Surface.ROTATION_90
                }
                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
                imageAnalysis?.targetRotation = rotation
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }


    private data class LensDiscoveryResult(
        val cameraId: String,
        val facing: Int,
        val focalLength: Float,
        val zoomState: ZoomState?,
        val rawSupported: Boolean
    )

    @OptIn(ExperimentalCamera2Interop::class)
    private fun discoverLenses(provider: ProcessCameraProvider) {
        val discovered = provider.availableCameraInfos.mapNotNull { info ->
            runCatching {
                val camera2 = Camera2CameraInfo.from(info)
                val facing = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ?: return@runCatching null
                val focal = camera2
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.minOrNull() ?: return@runCatching null
                val capabilities = camera2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val rawSupported = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
                val zoomState = info.zoomState.value
                LensDiscoveryResult(
                    cameraId = camera2.cameraId,
                    facing = facing,
                    focalLength = focal,
                    zoomState = zoomState,
                    rawSupported = rawSupported
                )
            }.getOrNull()
        }

        val entries = discovered.mapNotNull { result ->
            LensDescriptor(
                cameraId = result.cameraId,
                isFront = result.facing == CameraCharacteristics.LENS_FACING_FRONT,
                focalLength = result.focalLength,
                minRatio = result.zoomState?.minZoomRatio ?: 1.0f,
                maxRatio = result.zoomState?.maxZoomRatio ?: 1.0f,
                rawSupported = result.rawSupported
            )
        }

        Log.i(TAG, "Discovered cameras: $entries")

        val back = entries.filter { !it.isFront }
        val referenceFocal = back.maxByOrNull { it.focalLength }?.focalLength
            ?: back.firstOrNull()?.focalLength

        backLenses = if (referenceFocal == null || referenceFocal <= 0f) {
            emptyList()
        } else {
            back.map { descriptor ->
                LensOption(
                    cameraId = descriptor.cameraId,
                    selector = selectorForId(descriptor.cameraId),
                    isFront = false,
                    baseZoom = descriptor.focalLength / referenceFocal,
                    minRatio = descriptor.minRatio,
                    maxRatio = descriptor.maxRatio,
                    rawSupported = descriptor.rawSupported
                )
            }.sortedBy { it.baseZoom }
        }

        frontLens = entries.firstOrNull { it.isFront }?.let { descriptor ->
            LensOption(
                cameraId = descriptor.cameraId,
                selector = selectorForId(descriptor.cameraId),
                isFront = true,
                baseZoom = 1.0f,
                minRatio = descriptor.minRatio,
                maxRatio = descriptor.maxRatio,
                rawSupported = descriptor.rawSupported
            )
        }

        _availableZoomPresets.value = buildZoomPresets()
        Log.i(TAG, "Back lenses: $backLenses, presets: ${_availableZoomPresets.value}")
    }

    private data class LensDescriptor(
        val cameraId: String,
        val isFront: Boolean,
        val focalLength: Float,
        val minRatio: Float,
        val maxRatio: Float,
        val rawSupported: Boolean
    )

    @OptIn(ExperimentalCamera2Interop::class)
    private fun selectorForId(cameraId: String): CameraSelector =
        CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == cameraId }
            }
            .build()

    private fun buildZoomPresets(): List<Float> {
        val main = backLenses.lastOrNull { it.baseZoom >= 0.95f } ?: backLenses.lastOrNull()
            ?: return listOf(1.0f)
        val maxZoom = main.baseZoom * main.maxRatio
        val presets = mutableListOf<Float>()
        if (backLenses.any { it.baseZoom < 0.75f }) presets += 0.5f
        presets += 1.0f
        for (step in listOf(2.0f, 5.0f, 10.0f)) {
            if (step <= maxZoom + 0.01f) presets += step
        }
        return presets.distinct()
    }

    private fun lensFor(zoom: Float): LensOption? {
        if (backLenses.isEmpty()) return null
        return backLenses.lastOrNull { it.baseZoom <= zoom + 0.05f } ?: backLenses.first()
    }

    private fun targetLens(): LensOption? =
        if (_currentLens.value == LensFacing.FRONT) frontLens else lensFor(_zoomRatio.value)

    private fun useCaseSignature(): String {
        val cameraId = targetLens()?.cameraId ?: if (_currentLens.value == LensFacing.FRONT) "front" else "back"
        val pro = _proSettings.value
        val analysis = _cameraMode.value == CameraMode.PRO ||
            pro.focusPeakingEnabled || pro.zebraClippingEnabled
        return "${if (isVideoMode(_cameraMode.value)) "video" else "photo"}|" +
            "$analysis|$cameraId|${aspectGroup(_aspectRatio.value)}"
    }

    private fun isVideoMode(mode: CameraMode) =
        mode == CameraMode.VIDEO || mode == CameraMode.CINEMATIC

    fun startCamera() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val pView = previewView ?: return
        if (released) return

        val signature = useCaseSignature()
        if (signature == boundSignature && camera != null) return

        val wantsVideo = isVideoMode(_cameraMode.value)
        val pro = _proSettings.value
        val wantsAnalysis = _cameraMode.value == CameraMode.PRO ||
            pro.focusPeakingEnabled || pro.zebraClippingEnabled

        try {
            val selectedLens = targetLens()
            val fallbackSelector = if (_currentLens.value == LensFacing.FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val requestedSelector = selectedLens?.selector ?: fallbackSelector
            val cameraSelector = when {
                provider.hasCamera(requestedSelector) -> requestedSelector
                provider.hasCamera(fallbackSelector) -> fallbackSelector
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> {
                    Log.e(TAG, "No camera available")
                    _captureProgress.value =
                        CaptureProgress(CaptureState.IDLE, 0f, "No camera available")
                    return
                }
            }
            activeLens = selectedLens.takeIf { cameraSelector === requestedSelector }
            rawSupported = activeLens?.rawSupported == true

            provider.unbindAll()
            boundSignature = null

            val rotation = pView.display?.rotation ?: Surface.ROTATION_0

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspectRatioStrategy())
                .build()

            preview = Preview.Builder()
                .setTargetRotation(rotation)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.surfaceProvider = pView.surfaceProvider }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(rotation)
                .setResolutionSelector(resolutionSelector)
                .setCaptureMode(
                    if (_cameraMode.value == CameraMode.NIGHT_SIGHT) {
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    } else {
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    }
                )
                .setFlashMode(toCameraXFlashMode(_flashMode.value))
                .build()

            imageAnalysis = if (wantsAnalysis) {
                ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, ::processAnalysisFrame) }
            } else {
                null
            }

            videoCapture = if (wantsVideo) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                            FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
                        )
                    )
                    .build()
                VideoCapture.withOutput(recorder).also { it.targetRotation = rotation }
            } else {
                null
            }

            val useCases = listOfNotNull(preview, imageCapture, imageAnalysis, videoCapture)
            camera = provider.bindToLifecycle(owner, cameraSelector, *useCases.toTypedArray())
            boundSignature = signature

            readManualCapabilities()
            applyZoom(_zoomRatio.value)
            applyManualControls()
            if (_flashMode.value == FlashMode.TORCH) camera?.cameraControl?.enableTorch(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
            boundSignature = null
            camera = null
            _captureProgress.value =
                CaptureProgress(CaptureState.IDLE, 0f, "Camera configuration unsupported")
        }
    }


    @OptIn(ExperimentalCamera2Interop::class)
    private fun readManualCapabilities() {
        val info = camera?.cameraInfo ?: return
        val camera2 = runCatching { Camera2CameraInfo.from(info) }.getOrNull() ?: return
        val capabilities = camera2.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        )
        manualSensorSupported = capabilities?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) == true
        isoRange = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        exposureTimeRange = camera2.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )
        minFocusDistance = camera2.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f
        deviceSupportsVideoStabilization = camera2.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        )?.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
        _videoStabilizationSupported.value = deviceSupportsVideoStabilization
        _manualControlsSupported.value = manualSensorSupported
        Log.i(
            TAG,
            "Manual sensor=$manualSensorSupported iso=$isoRange exposure=$exposureTimeRange minFocus=$minFocusDistance videoStabilization=$deviceSupportsVideoStabilization"
        )
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyManualControls() {
        val control = camera?.cameraControl ?: return
        val pro = _proSettings.value
        val builder = CaptureRequestOptions.Builder()

        val wantsManualExposure = manualSensorSupported && (!pro.isIsoAuto || !pro.isShutterAuto)
        if (wantsManualExposure) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            isoRange?.let { range ->
                val iso = if (pro.isIsoAuto) range.lower else pro.iso
                builder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    iso.coerceIn(range.lower, range.upper)
                )
            }
            exposureTimeRange?.let { range ->
                val denominator = pro.shutterSpeedDenominator.coerceAtLeast(1L)
                val nanos = if (pro.isShutterAuto) range.lower else 1_000_000_000L / denominator
                builder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    nanos.coerceIn(range.lower, range.upper)
                )
            }
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        if (pro.isWbAuto) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        } else {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                kelvinToAwbMode(pro.kelvinWb)
            )
        }

        if (!pro.isFocusAuto && minFocusDistance > 0f) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                (pro.manualFocusDistance * minFocusDistance).coerceIn(0f, minFocusDistance)
            )
        } else {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        }

        if (deviceSupportsVideoStabilization) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (_videoStabilizationEnabled.value) {
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                } else {
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                }
            )
        }

        runCatching {
            Camera2CameraControl.from(control).setCaptureRequestOptions(builder.build())
        }.onFailure { Log.w(TAG, "Unable to apply manual controls", it) }
    }

    private fun kelvinToAwbMode(kelvin: Int): Int = when {
        kelvin <= 3000 -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
        kelvin <= 4200 -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
        kelvin <= 5200 -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
        kelvin <= 6500 -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
        else -> CaptureRequest.CONTROL_AWB_MODE_SHADE
    }

    private fun aspectRatioStrategy(): AspectRatioStrategy = when (_aspectRatio.value) {
        AspectRatio.RATIO_16_9, AspectRatio.RATIO_FULL ->
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        val previous = _aspectRatio.value
        super.setAspectRatio(ratio)
        if (aspectGroup(previous) != aspectGroup(ratio)) {
            boundSignature = null
            startCamera()
        }
    }

    private fun aspectGroup(ratio: AspectRatio): String = when (ratio) {
        AspectRatio.RATIO_16_9, AspectRatio.RATIO_FULL -> "wide"
        else -> "standard"
    }

    private fun toCameraXFlashMode(flash: FlashMode) = when (flash) {
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.ON, FlashMode.TORCH -> ImageCapture.FLASH_MODE_ON
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    }

    private fun processAnalysisFrame(imageProxy: ImageProxy) {
        try {
            val now = System.nanoTime()
            if (now - lastHistogramEmitNanos < HISTOGRAM_INTERVAL_NANOS) return
            lastHistogramEmitNanos = now

            val plane = imageProxy.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height

            val rBins = histogramRed.also { it.fill(0) }
            val gBins = histogramGreen.also { it.fill(0) }
            val bBins = histogramBlue.also { it.fill(0) }
            val lumBins = histogramLuma.also { it.fill(0) }

            val step = 4
            var sampleCount = 0
            if (histogramRowBuffer.size < rowStride) histogramRowBuffer = ByteArray(rowStride)
            val rowBytes = histogramRowBuffer

            for (y in 0 until height step step) {
                val rowStart = y * rowStride
                if (rowStart >= buffer.limit()) break
                buffer.position(rowStart)
                val bytesToRead = (width * pixelStride).coerceAtMost(buffer.remaining())
                if (bytesToRead <= 0) break
                buffer.get(rowBytes, 0, bytesToRead)

                for (x in 0 until width step step) {
                    val offset = x * pixelStride
                    if (offset + 2 >= bytesToRead) break
                    val r = rowBytes[offset].toInt() and 0xFF
                    val g = rowBytes[offset + 1].toInt() and 0xFF
                    val b = rowBytes[offset + 2].toInt() and 0xFF
                    val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

                    rBins[r * BIN_COUNT / 256]++
                    gBins[g * BIN_COUNT / 256]++
                    bBins[b * BIN_COUNT / 256]++
                    lumBins[lum.coerceIn(0, 255) * BIN_COUNT / 256]++
                    sampleCount++
                }
            }

            if (sampleCount > 0) {
                val peak = maxOf(
                    rBins.max(), gBins.max(), bBins.max(), lumBins.max()
                ).coerceAtLeast(1)
                _liveHistogram.value = HistogramData(
                    redBins = rBins.map { it * 100 / peak },
                    greenBins = gBins.map { it * 100 / peak },
                    blueBins = bBins.map { it * 100 / peak },
                    luminanceBins = lumBins.map { it * 100 / peak }
                )
            }

            updateExposureMask(buffer, pixelStride, rowStride, width, height)
        } catch (e: Exception) {
            Log.w(TAG, "Dropped histogram frame", e)
        } finally {
            imageProxy.close()
        }
    }

    override fun setMode(mode: CameraMode) {
        super.setMode(mode)
        startCamera()
    }

    override fun setLens(lens: LensFacing) {
        super.setLens(lens)
        startCamera()
        applyZoom(_zoomRatio.value)
    }

    override fun setZoom(zoom: Float) {
        super.setZoom(zoom)
        val desired = targetLens()
        if (desired != null && desired.cameraId != activeLens?.cameraId) {
            startCamera()
        } else {
            applyZoom(_zoomRatio.value)
        }
    }

    private fun applyZoom(requested: Float) {
        val control = camera?.cameraControl ?: return
        val zoomState = camera?.cameraInfo?.zoomState?.value
        val min = zoomState?.minZoomRatio ?: 1.0f
        val max = zoomState?.maxZoomRatio ?: 1.0f
        val base = activeLens?.baseZoom?.takeIf { it > 0f } ?: 1.0f
        control.setZoomRatio((requested / base).coerceIn(min, max))
    }

    override fun setFlash(flash: FlashMode) {
        super.setFlash(flash)
        val control = camera?.cameraControl
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        if (hasFlash) {
            if (flash == FlashMode.TORCH) {
                runCatching { control?.enableTorch(true) }
            } else {
                runCatching { control?.enableTorch(false) }
                imageCapture?.flashMode = toCameraXFlashMode(flash)
            }
        } else {
            // Front camera screen flash fallback
            runCatching { control?.enableTorch(false) }
            imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
        }
    }

    override fun setFocusPoint(x: Float, y: Float) {
        super.setFocusPoint(x, y)
        val pView = previewView ?: return
        if (pView.width == 0 || pView.height == 0) return
        val point = pView.meteringPointFactory.createPoint(x * pView.width, y * pView.height)
        val action = FocusMeteringAction
            .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        try {
            camera?.cameraControl?.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.w(TAG, "Focus/metering rejected", e)
        }
    }

    override fun updateProSettings(transform: (ProSettings) -> ProSettings) {
        super.updateProSettings(transform)
        val pro = _proSettings.value
        val exposureState = camera?.cameraInfo?.exposureState ?: return
        if (!exposureState.isExposureCompensationSupported) return
        val step = exposureState.exposureCompensationStep.toFloat()
        if (step <= 0f) return
        val index = kotlin.math.round(pro.evBias / step).toInt().coerceIn(
            exposureState.exposureCompensationRange.lower,
            exposureState.exposureCompensationRange.upper
        )
        camera?.cameraControl?.setExposureCompensationIndex(index)
        applyManualControls()
        startCamera()
    }


    override suspend fun refreshGallery() {
        val ctx = context ?: return
        val media = withContext(Dispatchers.IO) {
            buildList {
                addAll(queryMediaStore(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false))
                addAll(queryMediaStore(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true))
            }.sortedByDescending { it.timestamp }
        }
        _galleryList.value = media
        if (_recentMedia.value == null) _recentMedia.value = media.firstOrNull()
    }

    private fun queryMediaStore(ctx: Context, collection: Uri, isVideo: Boolean): List<CapturedMedia> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val selection: String?
        val args: Array<String>?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            args = arrayOf("%AuraCam%")
        } else {
            selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
            args = arrayOf("%/AuraCam/%")
        }

        return try {
            ctx.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor -> readMediaRows(cursor, collection, isVideo, ctx) } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Gallery query failed", e)
            emptyList()
        }
    }

    private fun readFileExif(
        ctx: Context,
        uri: Uri,
        width: Int,
        height: Int,
        mime: String,
        timestamp: Long
    ): ExifInfo {
        val fallback = ExifInfo(
            deviceModel = Build.MANUFACTURER + " " + Build.MODEL,
            lensFocalLength = "",
            iso = 0,
            shutterSpeed = "",
            aperture = "",
            exposureBias = "",
            whiteBalance = "",
            format = mime,
            resolution = "$width × $height",
            timestamp = ComputationalPipeline.formatTimestamp(timestamp),
            location = null
        )

        if (!mime.startsWith("image/")) return fallback

        return try {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = FloatArray(2)
                val hasLocation = exif.getLatLong(latLong)
                val focalMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    .takeIf { it > 0.0 }
                    ?: exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0.0)
                        .takeIf { it > 0.0 }
                val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                val bias = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0)

                fallback.copy(
                    deviceModel = listOfNotNull(
                        exif.getAttribute(ExifInterface.TAG_MAKE),
                        exif.getAttribute(ExifInterface.TAG_MODEL)
                    ).joinToString(" ").ifBlank { fallback.deviceModel },
                    lensFocalLength = focalMm?.let { "${(it * 100).toInt() / 100.0}mm" }.orEmpty(),
                    iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0),
                    shutterSpeed = formatExposureTime(exposure),
                    aperture = aperture?.takeIf { it.isNotBlank() }?.let { "f/$it" }.orEmpty(),
                    exposureBias = "${if (bias >= 0) "+" else ""}$bias EV",
                    whiteBalance = when (exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, 0)) {
                        1 -> "Manual"
                        else -> "Auto"
                    },
                    resolution = "${exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, width)} × " +
                        "${exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, height)}",
                    location = if (hasLocation) {
                        ComputationalPipeline.formatLocation(
                            GeoLocation(latLong[0].toDouble(), latLong[1].toDouble())
                        )
                    } else {
                        null
                    }
                )
            } ?: fallback
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read EXIF from $uri", e)
            fallback
        }
    }

    private fun formatExposureTime(seconds: Double): String = when {
        seconds <= 0.0 -> ""
        seconds >= 1.0 -> "${seconds}s"
        else -> "1/${kotlin.math.round(1.0 / seconds).toInt()}s"
    }

    private fun readMediaRows(
        cursor: Cursor,
        collection: Uri,
        isVideo: Boolean,
        ctx: Context?
    ): List<CapturedMedia> {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

        val items = mutableListOf<CapturedMedia>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: continue
            val timestamp = cursor.getLong(dateColumn) * 1000L
            val mime = cursor.getString(mimeColumn).orEmpty()
            val format = when {
                isVideo -> CaptureFormat.JPEG
                mime.contains("dng") -> CaptureFormat.RAW_DNG
                else -> CaptureFormat.JPEG
            }
            val itemUri = Uri.withAppendedPath(collection, id.toString())
            val width = cursor.getInt(widthColumn)
            val height = cursor.getInt(heightColumn)
            items += CapturedMedia(
                id = "${if (isVideo) "VID" else "IMG"}_$id",
                uri = itemUri.toString(),
                fileName = name,
                timestamp = timestamp,
                width = width,
                height = height,
                format = format,
                mode = if (isVideo) CameraMode.VIDEO else CameraMode.PHOTO,
                exif = ExifInfo(
                    deviceModel = "",
                    lensFocalLength = "",
                    iso = 0,
                    shutterSpeed = "",
                    aperture = "",
                    exposureBias = "",
                    whiteBalance = "",
                    format = mime,
                    resolution = "$width × $height",
                    timestamp = ComputationalPipeline.formatTimestamp(timestamp)
                )
            )
        }
        return items
    }

    override suspend fun loadExif(media: CapturedMedia): CapturedMedia {
        val ctx = context ?: return media
        if (media.exif.deviceModel.isNotBlank()) return media
        val uri = runCatching { Uri.parse(media.uri) }.getOrNull() ?: return media
        val enriched = withContext(Dispatchers.IO) {
            media.copy(
                exif = readFileExif(
                    ctx,
                    uri,
                    media.width,
                    media.height,
                    media.exif.format,
                    media.timestamp
                )
            )
        }
        _galleryList.value = _galleryList.value.map { if (it.id == enriched.id) enriched else it }
        if (_recentMedia.value?.id == enriched.id) _recentMedia.value = enriched
        return enriched
    }

    override suspend fun deleteMedia(media: CapturedMedia): Boolean {
        val ctx = context ?: return super.deleteMedia(media)
        val uri = runCatching { Uri.parse(media.uri) }.getOrNull()
            ?: return super.deleteMedia(media)
        val deleted = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }
        if (deleted) super.deleteMedia(media)
        return deleted
    }

    override suspend fun capturePhoto(): CapturedMedia {
        val ctx = context
        val capture = imageCapture

        if (ctx == null || capture == null) {
            return super.capturePhoto()
        }

        if (!captureInFlight.compareAndSet(false, true)) {
            return _recentMedia.value ?: super.capturePhoto()
        }

        try {
            val timerSec = _timerDuration.value.seconds
            if (timerSec > 0) {
                for (s in timerSec downTo 1) {
                    _captureProgress.value = CaptureProgress(
                        CaptureState.ALIGNING_FRAMES,
                        (timerSec - s + 1f) / timerSec,
                        "Timer: ${s}s"
                    )
                    delay(1000)
                }
            }

            _captureProgress.value = CaptureProgress(
                CaptureState.EXPOSURE_STACKING, 0.4f, "Capturing hardware sensor frame..."
            )

            val requestedFormat = _captureFormat.value
            val effectiveFormat = if (requestedFormat.isRaw && !canWriteDng) {
                Log.i(TAG, "RAW requested but unavailable (sensor=$rawSupported, writer=$DNG_WRITER_IMPLEMENTED); saving JPEG")
                CaptureFormat.JPEG
            } else {
                requestedFormat
            }
            if (effectiveFormat != requestedFormat) {
                _captureFormat.value = effectiveFormat
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "PXL_$timeStamp.${effectiveFormat.extension}"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(effectiveFormat))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AuraCam")
                }
            }

            val geoLocation = if (_geotaggingEnabled.value) locationProvider.lastKnownLocation() else null

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                ctx.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).setMetadata(
                ImageCapture.Metadata().apply {
                    location = geoLocation?.toAndroidLocation()
                    isReversedHorizontal = _currentLens.value == LensFacing.FRONT
                }
            ).build()

            val media = suspendCoroutine { continuation ->
                capture.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val savedUri = outputFileResults.savedUri ?: Uri.EMPTY
                            val exif = ComputationalPipeline.generateExif(
                                mode = _cameraMode.value,
                                lens = _currentLens.value,
                                zoom = _zoomRatio.value,
                                proSettings = _proSettings.value,
                                captureFormat = _captureFormat.value,
                                ultraHdr = _ultraHdrEnabled.value,
                                capturedAtEpochMillis = System.currentTimeMillis(),
                                location = geoLocation
                            )
                            val now = System.currentTimeMillis()
                            continuation.resume(
                                CapturedMedia(
                                    id = "IMG_$now",
                                    uri = savedUri.toString(),
                                    fileName = fileName,
                                    timestamp = now,
                                    width = 4080,
                                    height = 3072,
                                    format = effectiveFormat,
                                    mode = _cameraMode.value,
                                    exif = exif
                                )
                            )
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Image capture failed", exception)
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            }

            _recentMedia.value = media
            _galleryList.value = listOf(media) + _galleryList.value
            _captureProgress.value =
                CaptureProgress(CaptureState.COMPLETE, 1.0f, "Saved to DCIM/AuraCam")
            delay(600)
            return media
        } catch (e: Exception) {
            _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "Capture failed")
            delay(1200)
            throw e
        } finally {
            _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "")
            captureInFlight.set(false)
        }
    }

    private fun GeoLocation.toAndroidLocation(): Location =
        Location(LocationManager.GPS_PROVIDER).also {
            it.latitude = latitude
            it.longitude = longitude
            altitudeMeters?.let { altitude -> it.altitude = altitude }
            accuracyMeters?.let { accuracy -> it.accuracy = accuracy }
        }


    private fun updateExposureMask(
        buffer: ByteBuffer,
        pixelStride: Int,
        rowStride: Int,
        width: Int,
        height: Int
    ) {
        val pro = _proSettings.value
        if (!pro.focusPeakingEnabled && !pro.zebraClippingEnabled) {
            if (!_exposureMask.value.isEmpty) _exposureMask.value = ExposureMask()
            return
        }

        val gridWidth = width / MASK_STEP
        val gridHeight = height / MASK_STEP
        if (gridWidth < 3 || gridHeight < 3) return

        if (lumaGridWidth != gridWidth || lumaGridHeight != gridHeight) {
            lumaGrid = ByteArray(gridWidth * gridHeight)
            lumaGridWidth = gridWidth
            lumaGridHeight = gridHeight
        }

        val rowBytes = histogramRowBuffer
        for (gy in 0 until gridHeight) {
            val rowStart = gy * MASK_STEP * rowStride
            if (rowStart >= buffer.limit()) break
            buffer.position(rowStart)
            val bytesToRead = (width * pixelStride).coerceAtMost(buffer.remaining())
            if (bytesToRead <= 0) break
            buffer.get(rowBytes, 0, bytesToRead)

            for (gx in 0 until gridWidth) {
                val offset = gx * MASK_STEP * pixelStride
                if (offset + 2 >= bytesToRead) break
                val r = rowBytes[offset].toInt() and 0xFF
                val g = rowBytes[offset + 1].toInt() and 0xFF
                val b = rowBytes[offset + 2].toInt() and 0xFF
                lumaGrid[gy * gridWidth + gx] =
                    ((0.299f * r + 0.587f * g + 0.114f * b).toInt() shr 1).toByte()
            }
        }

        val cells = gridWidth * gridHeight
        val peaking = if (pro.focusPeakingEnabled) ByteArray(cells) else ByteArray(0)
        val zebra = if (pro.zebraClippingEnabled) ByteArray(cells) else ByteArray(0)

        for (gy in 1 until gridHeight - 1) {
            for (gx in 1 until gridWidth - 1) {
                val index = gy * gridWidth + gx
                val centre = lumaGrid[index].toInt() and 0xFF

                if (zebra.isNotEmpty() && centre >= ZEBRA_THRESHOLD) {
                    zebra[index] = 1
                }

                if (peaking.isNotEmpty()) {
                    val right = lumaGrid[index + 1].toInt() and 0xFF
                    val below = lumaGrid[index + gridWidth].toInt() and 0xFF
                    val gradient = kotlin.math.abs(centre - right) + kotlin.math.abs(centre - below)
                    if (gradient >= PEAKING_THRESHOLD) peaking[index] = 1
                }
            }
        }

        _exposureMask.value = ExposureMask(gridWidth, gridHeight, peaking, zebra)
    }

    private fun mimeTypeFor(format: CaptureFormat) = when (format) {
        CaptureFormat.RAW_DNG, CaptureFormat.RAW_PLUS_JPEG -> "image/x-adobe-dng"
        else -> "image/jpeg"
    }

    override suspend fun toggleVideoRecording() {
        val ctx = context
        val video = videoCapture
        if (ctx == null || video == null) {
            super.toggleVideoRecording()
            return
        }

        if (_isRecording.value) {
            activeRecording?.stop()
            activeRecording = null
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "VID_$timeStamp.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AuraCam")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            ctx.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        try {
            val pending = video.output.prepareRecording(ctx, mediaStoreOutput)
            if (hasPermission(ctx, Manifest.permission.RECORD_AUDIO)) {
                pending.withAudioEnabled()
            }
            activeRecording = pending.start(ContextCompat.getMainExecutor(ctx)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        _recordingDurationSeconds.value = 0
                    }

                    is VideoRecordEvent.Status -> {
                        _recordingDurationSeconds.value =
                            (recordEvent.recordingStats.recordedDurationNanos / 1_000_000_000).toInt()
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (recordEvent.hasError()) {
                            Log.e(TAG, "Recording error ${recordEvent.error}", recordEvent.cause)
                        }
                        activeRecording = null
                        _isRecording.value = false
                        _recordingDurationSeconds.value = 0
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            activeRecording = null
            _isRecording.value = false
        }
    }

    private fun hasPermission(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun release() {
        if (released) return
        released = true
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Recording already stopped", e)
        }
        activeRecording = null
        orientationListener?.disable()
        orientationListener = null
        sensorLeveler.stop()
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        boundSignature = null
        camera = null
        preview = null
        imageCapture = null
        imageAnalysis = null
        videoCapture = null
        cameraProvider = null
        previewView = null
        lifecycleOwner = null
        context = null
        cameraExecutor.shutdown()
        analysisExecutor.shutdown()
        super.release()
    }

    private val canWriteDng: Boolean
        get() = rawSupported && DNG_WRITER_IMPLEMENTED

    private companion object {
        const val BIN_COUNT = 32
        const val DNG_WRITER_IMPLEMENTED = false
        const val HISTOGRAM_INTERVAL_NANOS = 100_000_000L
        const val MASK_STEP = 8
        const val ZEBRA_THRESHOLD = 123
        const val PEAKING_THRESHOLD = 14
    }
}
