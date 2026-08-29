package com.auracam.camera.domain

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.auracam.processing.ComputationalPipeline
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

actual class PlatformCameraEngine : BaseCameraEngine() {

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
    private val analysisExecutor = Executors.newFixedThreadPool(2)

    fun bindToLifecycle(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        this.context = context.applicationContext
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                startCamera()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun startCamera() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val pView = previewView ?: return

        try {
            provider.unbindAll()

            val lens = _currentLens.value
            val cameraSelector = if (lens == LensFacing.FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // 1. Preview Use Case
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(pView.surfaceProvider)
                }

            // 2. ImageCapture Use Case (Zero Shutter Lag + High Quality)
            val captureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(when (_flashMode.value) {
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.ON, FlashMode.TORCH -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                })

            imageCapture = captureBuilder.build()

            // 3. ImageAnalysis Use Case (Live 30fps frame analyzer for live RGB Histogram)
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processAnalysisFrame(imageProxy)
                    }
                }

            // 4. VideoCapture Use Case
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Bind to lifecycle
            camera = provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis,
                videoCapture
            )

            // Apply initial zoom & flash settings
            camera?.cameraControl?.setZoomRatio(_zoomRatio.value.coerceIn(1.0f, 10.0f))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processAnalysisFrame(imageProxy: ImageProxy) {
        try {
            val plane = imageProxy.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height

            val rBins = IntArray(32)
            val gBins = IntArray(32)
            val bBins = IntArray(32)
            val lumBins = IntArray(32)

            // Sample pixels with a step to keep 60fps performance
            val step = 4
            var sampleCount = 0
            val rowBytes = ByteArray(rowStride)

            for (y in 0 until height step step) {
                buffer.position(y * rowStride)
                val bytesToRead = (width * pixelStride).coerceAtMost(buffer.remaining())
                buffer.get(rowBytes, 0, bytesToRead)

                for (x in 0 until width step step) {
                    val offset = x * pixelStride
                    if (offset + 2 < bytesToRead) {
                        val r = rowBytes[offset].toInt() and 0xFF
                        val g = rowBytes[offset + 1].toInt() and 0xFF
                        val b = rowBytes[offset + 2].toInt() and 0xFF
                        val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

                        rBins[r * 32 / 256]++
                        gBins[g * 32 / 256]++
                        bBins[b * 32 / 256]++
                        lumBins[lum * 32 / 256]++
                        sampleCount++
                    }
                }
            }

            if (sampleCount > 0) {
                val maxBin = (rBins.maxOrNull() ?: 1).coerceAtLeast(1)
                _liveHistogram.value = HistogramData(
                    redBins = rBins.map { (it * 100 / maxBin).coerceIn(5, 100) },
                    greenBins = gBins.map { (it * 100 / maxBin).coerceIn(5, 100) },
                    blueBins = bBins.map { (it * 100 / maxBin).coerceIn(5, 100) },
                    luminanceBins = lumBins.map { (it * 100 / maxBin).coerceIn(5, 100) }
                )
            }
        } catch (e: Exception) {
            // Frame analysis skip
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
    }

    override fun setZoom(zoom: Float) {
        super.setZoom(zoom)
        camera?.cameraControl?.setZoomRatio(zoom.coerceIn(1.0f, 10.0f))
    }

    override fun setFlash(flash: FlashMode) {
        super.setFlash(flash)
        when (flash) {
            FlashMode.TORCH -> camera?.cameraControl?.enableTorch(true)
            else -> {
                camera?.cameraControl?.enableTorch(false)
                imageCapture?.flashMode = when (flash) {
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            }
        }
    }

    override fun setFocusPoint(x: Float, y: Float) {
        super.setFocusPoint(x, y)
        val pView = previewView ?: return
        val factory = pView.meteringPointFactory
        val point = factory.createPoint(x * pView.width, y * pView.height)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    override fun updateProSettings(transform: (ProSettings) -> ProSettings) {
        super.updateProSettings(transform)
        val pro = _proSettings.value
        val exposureState = camera?.cameraInfo?.exposureState
        if (exposureState != null && exposureState.isExposureCompensationSupported) {
            val step = exposureState.exposureCompensationStep.toFloat()
            if (step > 0f) {
                val index = (pro.evBias / step).toInt().coerceIn(
                    exposureState.exposureCompensationRange.lower,
                    exposureState.exposureCompensationRange.upper
                )
                camera?.cameraControl?.setExposureCompensationIndex(index)
            }
        }
    }

    override suspend fun capturePhoto(): CapturedMedia {
        val ctx = context
        val capture = imageCapture

        if (ctx == null || capture == null) {
            return super.capturePhoto()
        }

        // Show computational pipeline countdown / progress
        val timerSec = _timerDuration.value.seconds
        if (timerSec > 0) {
            for (s in timerSec downTo 1) {
                _captureProgress.value = CaptureProgress(CaptureState.ALIGNING_FRAMES, (timerSec - s + 1f) / timerSec, "Timer: ${s}s")
                delay(1000)
            }
        }

        _captureProgress.value = CaptureProgress(CaptureState.EXPOSURE_STACKING, 0.4f, "Capturing hardware sensor frame...")

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "PXL_${timeStamp}.${_captureFormat.value.extension}"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (_captureFormat.value == CaptureFormat.RAW_DNG) "image/x-adobe-dng" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AuraCam")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            ctx.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        return suspendCoroutine { continuation ->
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
                            ultraHdr = _ultraHdrEnabled.value
                        )

                        val media = CapturedMedia(
                            id = "IMG_${System.currentTimeMillis()}",
                            uri = savedUri.toString(),
                            fileName = fileName,
                            timestamp = System.currentTimeMillis(),
                            width = 4080,
                            height = 3072,
                            format = _captureFormat.value,
                            mode = _cameraMode.value,
                            exif = exif
                        )

                        _recentMedia.value = media
                        _galleryList.value = listOf(media) + _galleryList.value
                        _captureProgress.value = CaptureProgress(CaptureState.COMPLETE, 1.0f, "Saved to DCIM/AuraCam")

                        continuation.resume(media)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                        _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "")
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
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
            _isRecording.value = false
            _recordingDurationSeconds.value = 0
        } else {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "VID_${timeStamp}.mp4"

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

            activeRecording = video.output
                .prepareRecording(ctx, mediaStoreOutput)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(ctx)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            _isRecording.value = true
                            _recordingDurationSeconds.value = 0
                        }
                        is VideoRecordEvent.Status -> {
                            _recordingDurationSeconds.value = (recordEvent.recordingStats.recordedDurationNanos / 1_000_000_000).toInt()
                        }
                        is VideoRecordEvent.Finalize -> {
                            _isRecording.value = false
                            _recordingDurationSeconds.value = 0
                        }
                    }
                }
        }
    }
}
