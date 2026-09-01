package com.auracam.camera.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.Face
import android.location.Location
import android.location.LocationManager
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.database.Cursor
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.auracam.camera.camera2.CameraHardware
import com.auracam.camera.camera2.Camera2Stream
import com.auracam.camera.camera2.CaptureRequestTuner
import com.auracam.camera.camera2.CompositeRecorder
import com.auracam.camera.camera2.MediaStoreWriter
import com.auracam.camera.gl.CameraCompositor
import com.auracam.camera.gl.CenterCrop
import com.auracam.camera.gl.StreamSlot
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "AuraCamEngine"

/**
 * Android camera engine built directly on Camera2.
 *
 * Every stream the app shows or records goes through one GL compositor:
 *   camera(s) -> SurfaceTexture -> [CameraCompositor] -> preview surface (+ encoder surface)
 *
 * That single path is what makes Dual Vlog behave. Layout, swap and tone filters are uniforms on
 * the composited frame, so changing them never reconfigures a capture session, and the recorded
 * MP4 is the same composite the viewfinder shows rather than a separate re-derivation of it.
 */
actual class PlatformCameraEngine : BaseCameraEngine(simulateSensors = false) {

    private var context: Context? = null
    private var hardware: CameraHardware? = null

    private val compositor = CameraCompositor()
    private var recorder: CompositeRecorder? = null

    private var rearStream: Camera2Stream? = null
    private var frontStream: Camera2Stream? = null

    private var previewSurface: Surface? = null
    private var attachedSurface: Surface? = null
    private var previewWidth = 0
    private var previewHeight = 0
    private var displayRotationDegrees = 0

    private var stillReader: ImageReader? = null
    private var analysisReader: ImageReader? = null

    // HAL callbacks land on [cameraHandler]; session orchestration runs on [sessionHandler].
    // They must be different threads: closing a device blocks until the HAL confirms on the
    // callback thread, so orchestrating from that same thread would deadlock.
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var sessionThread: HandlerThread? = null
    private var sessionHandler: Handler? = null

    private val sensorLeveler = PlatformSensorLeveler()
    private val locationProvider = PlatformLocationProvider()
    private var orientationListener: OrientationEventListener? = null

    private val captureInFlight = AtomicBoolean(false)
    private var recordingTicker: Job? = null

    private var activeLens: CameraHardware.Lens? = null
    private var secondaryLens: CameraHardware.Lens? = null
    private var sessionSignature: String? = null

    /** Bumped on every (re)configuration so callbacks from a superseded attempt are dropped. */
    private var configureGeneration = 0

    private var meteringPoint: Pair<Float, Float>? = null
    private var lastTrackingEmitNanos = 0L

    /** Latched subject box per stream, in that stream's own sensor-normalized coordinates. */
    private var rearTrackingBox: FloatArray? = null
    private var frontTrackingBox: FloatArray? = null

    /** Configured stream dimensions, needed to reproduce the compositor's center-crop. */
    private var rearStreamSize: Size? = null
    private var frontStreamSize: Size? = null

    private val histogramRed = IntArray(BIN_COUNT)
    private val histogramGreen = IntArray(BIN_COUNT)
    private val histogramBlue = IntArray(BIN_COUNT)
    private val histogramLuma = IntArray(BIN_COUNT)
    private var lastHistogramEmitNanos = 0L
    private var lumaGrid = ByteArray(0)
    private var lumaGridWidth = 0
    private var lumaGridHeight = 0

    @Volatile
    private var released = false

    // ---------------------------------------------------------------------------------------
    // Preview attachment
    // ---------------------------------------------------------------------------------------

    /**
     * Attaches the viewfinder's output surface. Called when the `SurfaceView` becomes available
     * and whenever it is resized; the surface's own lifecycle is the camera's lifecycle, so
     * backgrounding the app tears the session down without a `LifecycleOwner`.
     */
    fun attachPreview(context: Context, surface: Surface, width: Int, height: Int, rotation: Int) {
        if (released) return
        val appContext = context.applicationContext
        val firstAttach = this.context == null
        this.context = appContext
        this.previewSurface = surface
        this.previewWidth = width
        this.previewHeight = height
        val rotationDegrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val rotationChanged = this.displayRotationDegrees != rotationDegrees
        this.displayRotationDegrees = rotationDegrees

        if (firstAttach) {
            locationProvider.initialize(appContext)
            startSensorLeveler(appContext)
            startOrientationListener(appContext)
            coroutineScope.launch { refreshGallery() }
        }
        if (hardware == null) {
            hardware = runCatching { CameraHardware(appContext) }.getOrElse {
                Log.e(TAG, "Camera hardware unavailable", it)
                _captureProgress.value =
                    CaptureProgress(CaptureState.IDLE, 0f, "Camera unavailable on this device")
                return
            }
            _availableZoomPresets.value = hardware?.zoomPresets() ?: listOf(1.0f)
        }
        ensureCameraThread()
        compositor.start()
        compositor.setPreviewOutput(surface, width, height)

        // A resize (e.g. hiding the zoom bar when entering Dual Vlog) only changes the
        // compositor's output geometry. Restarting the capture session here would race the
        // in-flight configuration and leave a dead feed, so only a genuinely new surface
        // reconfigures the cameras.
        val surfaceChanged = attachedSurface !== surface
        attachedSurface = surface
        if (surfaceChanged || rearStream == null) {
            configureSession(force = true)
        } else {
            if (rotationChanged) refreshStreamOrientation()
            pushLayout()
        }
    }

    fun detachPreview() {
        previewSurface = null
        attachedSurface = null
        compositor.setPreviewOutput(null, 0, 0)
        sessionSignature = null
        sessionHandler?.post { closeStreams() }
    }

    /** Re-applies texture rotation after a display rotation, without touching the session. */
    private fun refreshStreamOrientation() {
        rearStream?.lens?.let {
            compositor.updateStreamOrientation(StreamSlot.REAR, textureRotationFor(it), it.isFront)
        }
        frontStream?.lens?.let {
            compositor.updateStreamOrientation(StreamSlot.FRONT, textureRotationFor(it), it.isFront)
        }
        publishTrackedSubjects()
    }

    private fun ensureCameraThread() {
        if (cameraHandler == null) {
            val thread = HandlerThread("AuraCamCamera2").also { it.start() }
            cameraThread = thread
            cameraHandler = Handler(thread.looper)
        }
        if (sessionHandler == null) {
            val thread = HandlerThread("AuraCamSession").also { it.start() }
            sessionThread = thread
            sessionHandler = Handler(thread.looper)
        }
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
                val degrees = when {
                    orientation >= 315 || orientation < 45 -> 0
                    orientation < 135 -> 90
                    orientation < 225 -> 180
                    else -> 270
                }
                if (degrees != deviceOrientationDegrees) {
                    deviceOrientationDegrees = degrees
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    @Volatile
    private var deviceOrientationDegrees = 0

    // ---------------------------------------------------------------------------------------
    // Session configuration
    // ---------------------------------------------------------------------------------------

    private fun isVideoMode(mode: CameraMode) =
        mode == CameraMode.VIDEO ||
            mode == CameraMode.CINEMATIC ||
            mode == CameraMode.DUAL_VLOG ||
            mode == CameraMode.SLOW_MOTION ||
            mode == CameraMode.TIME_LAPSE

    private fun wantsAnalysis(): Boolean {
        val pro = _proSettings.value
        return _cameraMode.value == CameraMode.PRO ||
            pro.focusPeakingEnabled ||
            pro.zebraClippingEnabled
    }

    private fun sessionSignature(): String {
        val dual = _cameraMode.value == CameraMode.DUAL_VLOG
        val lensId = if (dual) "dual" else targetLens()?.cameraId.orEmpty()
        return listOf(
            if (isVideoMode(_cameraMode.value)) "video" else "photo",
            lensId,
            aspectGroup(_aspectRatio.value),
            _photoResolution.value.name,
            wantsAnalysis().toString()
        ).joinToString("|")
    }

    private fun targetLens(): CameraHardware.Lens? {
        val hw = hardware ?: return null
        return if (_currentLens.value == LensFacing.FRONT) {
            hw.frontLens ?: hw.backLensFor(_zoomRatio.value)
        } else {
            hw.backLensFor(_zoomRatio.value) ?: hw.frontLens
        }
    }

    private fun aspectGroup(ratio: AspectRatio): String = when (ratio) {
        AspectRatio.RATIO_16_9, AspectRatio.RATIO_FULL -> "wide"
        AspectRatio.RATIO_1_1 -> "square"
        else -> "standard"
    }

    private fun targetAspect(): Float = when (_aspectRatio.value) {
        AspectRatio.RATIO_16_9, AspectRatio.RATIO_FULL -> 16f / 9f
        AspectRatio.RATIO_1_1 -> 1f
        else -> 4f / 3f
    }

    /**
     * Clockwise rotation needed to show a stream upright in the viewfinder.
     *
     * This uses the *display* rotation, not the accelerometer's idea of which way the handset is
     * tilted: the viewfinder is laid out by the window, so a user filming while lying down must
     * still see an upright preview. The physical orientation only decides how captures are
     * tagged, in [jpegOrientation].
     *
     * The front camera gets the extra `360 - result` step because its image is mirrored for
     * display; without it a 270°-mounted front sensor lands 180° away from the rear sensor.
     */
    private fun displayRotationFor(lens: CameraHardware.Lens): Int {
        val device = displayRotationDegrees
        return if (lens.isFront) {
            val raw = (lens.sensorOrientation + device) % 360
            (360 - raw) % 360
        } else {
            (lens.sensorOrientation - device + 360) % 360
        }
    }

    /**
     * Rotation the compositor applies to texture coordinates.
     *
     * Texture space has Y up, so a positive rotation of the coordinates makes the sampled image
     * appear rotated clockwise by the same amount — the texture rotation equals the display
     * rotation rather than opposing it.
     */
    private fun textureRotationFor(lens: CameraHardware.Lens): Int = displayRotationFor(lens)

    /**
     * Serialises all session (re)configuration onto the session thread so opens and closes never
     * overlap, and so the UI thread never blocks on the HAL.
     */
    private fun configureSession(force: Boolean = false) {
        if (released) return
        ensureCameraThread()
        sessionHandler?.post { configureSessionNow(force) }
    }

    private fun configureSessionNow(force: Boolean) {
        if (released) return
        val ctx = context ?: return
        val hw = hardware ?: return
        previewSurface ?: return
        if (!hasPermission(ctx, Manifest.permission.CAMERA)) return

        val signature = sessionSignature()
        if (!force && signature == sessionSignature && rearStream?.isReady == true) return

        closeStreams()
        val generation = ++configureGeneration

        val dual = _cameraMode.value == CameraMode.DUAL_VLOG
        if (dual) configureDualSession(ctx, hw, generation)
        else configureSingleSession(ctx, hw, generation)
        sessionSignature = signature
        pushLayout()
    }

    /** True while [generation] is still the configuration the engine is acting on. */
    private fun isCurrent(generation: Int) = !released && generation == configureGeneration

    private fun configureSingleSession(ctx: Context, hw: CameraHardware, generation: Int) {
        val lens = targetLens() ?: run {
            _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "No camera available")
            return
        }
        activeLens = lens
        secondaryLens = null

        val aspect = targetAspect()
        val previewSize = hw.bestPreviewSize(lens, Size(1920, 1080), aspect)
        rearStreamSize = previewSize
        frontStreamSize = null
        val input = compositor.acquireInputSurface(
            StreamSlot.REAR,
            previewSize.width,
            previewSize.height,
            textureRotationFor(lens),
            lens.isFront
        ) ?: return
        compositor.releaseInputSurface(StreamSlot.FRONT)

        val extras = mutableListOf<Surface>()
        stillReader = createStillReader(hw, lens, aspect)?.also { extras += it.surface }
        analysisReader = if (wantsAnalysis()) {
            createAnalysisReader(hw, lens, aspect)?.also { extras += it.surface }
        } else {
            null
        }

        val stream = Camera2Stream(ctx, lens, requireHandler())
        stream.onResult = { result -> onCaptureResult(result, lens) }
        rearStream = stream
        stream.openDevice(
            onOpened = {
                if (!isCurrent(generation)) return@openDevice
                stream.configure(
                    repeating = listOfNotNull(input, analysisReader?.surface),
                    extra = listOfNotNull(stillReader?.surface),
                    frame = buildFrame(lens),
                    onReady = { applyPostConfigureState() },
                    onError = { message -> reportSessionError(message) }
                )
            },
            onError = { message -> reportSessionError(message) }
        )
    }

    /**
     * Dual Vlog opens both physical cameras. The ids come from the HAL's own concurrent-camera
     * list; when the device advertises none, we fall back to a single stream rather than showing
     * a dead half.
     */
    private fun configureDualSession(ctx: Context, hw: CameraHardware, generation: Int) {
        val pair = hw.concurrentPair
        if (pair == null) {
            Log.w(TAG, "Device reports no concurrent camera pair; Dual Vlog falls back to one feed")
            _captureProgress.value = CaptureProgress(
                CaptureState.IDLE, 0f, "Dual camera unsupported on this device"
            )
            configureSingleSession(ctx, hw, generation)
            return
        }
        val rearLens = hw.lensById(pair.first) ?: return
        val frontLens = hw.lensById(pair.second) ?: return
        activeLens = rearLens
        secondaryLens = frontLens

        // Concurrent operation is only guaranteed up to 720p per stream.
        val cap = Size(1280, 720)
        val aspect = 4f / 3f
        val rearSize = hw.bestPreviewSize(rearLens, cap, aspect)
        val frontSize = hw.bestPreviewSize(frontLens, cap, aspect)
        rearStreamSize = rearSize
        frontStreamSize = frontSize

        val rearInput = compositor.acquireInputSurface(
            StreamSlot.REAR, rearSize.width, rearSize.height,
            textureRotationFor(rearLens), false
        ) ?: return
        val frontInput = compositor.acquireInputSurface(
            StreamSlot.FRONT, frontSize.width, frontSize.height,
            textureRotationFor(frontLens), true
        ) ?: return

        stillReader = null
        analysisReader = null

        val rear = Camera2Stream(ctx, rearLens, requireHandler())
        rear.onResult = { result -> onCaptureResult(result, rearLens) }
        val front = Camera2Stream(ctx, frontLens, requireHandler())
        // Both streams report faces: in Dual Vlog the subject worth tracking is usually the
        // presenter on the front camera, not whatever the rear lens happens to see.
        front.onResult = { result -> onCaptureResult(result, frontLens) }
        rearStream = rear
        frontStream = front

        // Both devices must be open before either session is configured (see Camera2Stream.openDevice).
        rear.openDevice(
            onOpened = {
                if (!isCurrent(generation)) return@openDevice
                front.openDevice(
                    onOpened = {
                        if (!isCurrent(generation)) return@openDevice
                        rear.configure(
                            repeating = listOf(rearInput),
                            frame = buildFrame(rearLens),
                            onReady = { applyPostConfigureState() },
                            onError = { message -> reportSessionError(message) }
                        )
                        front.configure(
                            repeating = listOf(frontInput),
                            frame = buildFrame(frontLens),
                            onReady = { pushLayout() },
                            onError = { message ->
                                Log.w(TAG, "Secondary vlog session failed: $message")
                                dropSecondaryVlogStream()
                            }
                        )
                    },
                    onError = { message ->
                        if (!isCurrent(generation)) return@openDevice
                        Log.w(TAG, "Secondary vlog camera unavailable: $message")
                        dropSecondaryVlogStream()
                        // Fall back to a single live feed rather than showing a dead half.
                        rear.configure(
                            repeating = listOf(rearInput),
                            frame = buildFrame(rearLens),
                            onReady = { applyPostConfigureState() },
                            onError = { m -> reportSessionError(m) }
                        )
                    }
                )
            },
            onError = { message -> reportSessionError(message) }
        )
    }

    /** Tears down the second vlog feed and re-renders as a single full-frame stream. */
    private fun dropSecondaryVlogStream() {
        val front = frontStream ?: return
        frontStream = null
        sessionHandler?.post {
            front.close()
            compositor.releaseInputSurface(StreamSlot.FRONT)
            pushLayout()
        }
    }

    private fun reportSessionError(message: String) {
        Log.e(TAG, "Session error: $message")
        _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, message)
    }

    private fun requireHandler(): Handler {
        ensureCameraThread()
        return cameraHandler!!
    }

    private fun createStillReader(
        hw: CameraHardware,
        lens: CameraHardware.Lens,
        aspect: Float
    ): ImageReader? {
        val cap = when (_photoResolution.value) {
            PhotoResolution.HIGH_50MP -> Size(8160, 6120)
            PhotoResolution.STANDARD_12MP -> Size(4080, 3060)
            PhotoResolution.SAVER_8MP -> Size(3264, 2448)
        }
        val size = hw.bestSize(lens, ImageFormat.JPEG, cap, aspect) ?: return null
        return ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
    }

    private fun createAnalysisReader(
        hw: CameraHardware,
        lens: CameraHardware.Lens,
        aspect: Float
    ): ImageReader? {
        val size = hw.bestSize(lens, ImageFormat.YUV_420_888, Size(640, 480), aspect)
            ?: return null
        return ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            .also { reader ->
                reader.setOnImageAvailableListener({ r ->
                    val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                    try {
                        processAnalysisFrame(image)
                    } finally {
                        image.close()
                    }
                }, requireHandler())
            }
    }

    private fun applyPostConfigureState() {
        val lens = activeLens ?: return
        readCapabilities(lens)
        pushLayout()
    }

    private fun buildFrame(lens: CameraHardware.Lens): CaptureRequestTuner.Frame {
        // Digital zoom is relative to this lens's own optical position.
        val digital = if (lens.isFront || lens.baseZoom <= 0f) {
            _zoomRatio.value
        } else {
            _zoomRatio.value / lens.baseZoom
        }
        return CaptureRequestTuner.Frame(
            pro = _proSettings.value,
            flash = _flashMode.value,
            digitalZoom = digital.coerceAtLeast(1.0f),
            videoStabilization = _videoStabilizationEnabled.value,
            isVideo = isVideoMode(_cameraMode.value),
            meteringPoint = meteringPoint,
            trackingBox = if (lens.isFront) frontTrackingBox else rearTrackingBox,
            faceTracking = _subjectTrackingEnabled.value
        )
    }

    private fun refreshRepeating() {
        rearStream?.let { it.setRepeating(buildFrame(it.lens)) }
        frontStream?.let { it.setRepeating(buildFrame(it.lens)) }
    }

    private fun readCapabilities(lens: CameraHardware.Lens) {
        val tuner = rearStream?.tuner ?: return
        _manualControlsSupported.value = tuner.manualSensorSupported
        _videoStabilizationSupported.value = tuner.videoStabilizationSupported
        _hardwareQualityStatus.value = HardwareQualityStatus(
            hardwareLevelName = tuner.hardwareLevelName,
            oisSupported = tuner.opticalStabilizationSupported,
            oisActive = tuner.opticalStabilizationSupported && _proSettings.value.oisEnabled,
            maxResolutionMegapixels = tuner.sensorMegapixels,
            highQualityDenoiseActive = _proSettings.value.hardwareDenoiseQuality,
            edgeEnhancementActive = _proSettings.value.edgeSharpeningBoost,
            chromaticAberrationCorrectionActive = true,
            distortionCorrectionActive = true,
            toneMappingActive = true,
            uncompressedJpegQuality = 100
        )
        Log.i(
            TAG,
            "Camera ${lens.cameraId} level=${tuner.hardwareLevelName} " +
                "faceTracking=${tuner.supportsFaceTracking} manual=${tuner.manualSensorSupported}"
        )
    }

    // ---------------------------------------------------------------------------------------
    // Hardware subject tracking
    // ---------------------------------------------------------------------------------------

    /**
     * Reads the HAL's own per-frame scene analysis.
     *
     * Faces detected by the camera hardware are mapped into viewport space — through the same
     * rotation, mirroring and center-crop the compositor applies — so the reticle sits on the
     * subject, and the winning box is fed back as the AF/AE metering region on the next request
     * so focus and exposure follow that subject.
     */
    private fun onCaptureResult(result: TotalCaptureResult, lens: CameraHardware.Lens) {
        if (released) return
        if (!_subjectTrackingEnabled.value) {
            if (rearTrackingBox != null || frontTrackingBox != null) {
                rearTrackingBox = null
                frontTrackingBox = null
                _trackedSubjects.value = emptyList()
            }
            return
        }

        val now = System.nanoTime()
        if (now - lastTrackingEmitNanos < TRACKING_INTERVAL_NANOS) return
        lastTrackingEmitNanos = now

        val stream = if (lens.isFront) frontStream else rearStream
        val tuner = stream?.tuner ?: return
        val faces = result.get(CaptureResult.STATISTICS_FACES)?.toList().orEmpty()

        if (faces.isEmpty()) {
            val had = if (lens.isFront) frontTrackingBox != null else rearTrackingBox != null
            if (had) {
                if (lens.isFront) frontTrackingBox = null else rearTrackingBox = null
                publishTrackedSubjects()
                stream.setRepeating(buildFrame(lens))
            }
            return
        }

        val active = tuner.activeArray
        if (active.width() <= 0 || active.height() <= 0) return

        // Largest face wins: in a vlog frame that is the person actually talking to camera.
        val primary = faces.maxByOrNull {
            (it.bounds.width().toLong()) * it.bounds.height()
        } ?: return

        val box = floatArrayOf(
            (primary.bounds.left - active.left).toFloat() / active.width(),
            (primary.bounds.top - active.top).toFloat() / active.height(),
            (primary.bounds.right - active.left).toFloat() / active.width(),
            (primary.bounds.bottom - active.top).toFloat() / active.height()
        )

        val previous = if (lens.isFront) frontTrackingBox else rearTrackingBox
        val moved = previous == null || (0..3).any { abs(previous[it] - box[it]) > TRACKING_DEADZONE }
        if (lens.isFront) frontTrackingBox = box else rearTrackingBox = box
        publishTrackedSubjects()
        if (moved) stream.setRepeating(buildFrame(lens))
    }

    /**
     * Converts the latched per-stream sensor boxes into viewport rectangles the UI can draw,
     * honouring the current Dual Vlog layout so a reticle lands inside the half it belongs to.
     */
    private fun publishTrackedSubjects() {
        val dual = _cameraMode.value == CameraMode.DUAL_VLOG && frontStream != null
        val frames = if (dual) {
            DualVlogNormalizedGeometry.framesFor(
                _dualVlogLayout.value,
                _isDualStreamSwapped.value,
                _dualVlogPipRect.value
            )
        } else {
            null
        }

        val subjects = buildList {
            rearStream?.lens?.let { lens ->
                rearTrackingBox?.let { box ->
                    viewportSubject(box, lens, rearStreamSize, frames?.rear)?.let(::add)
                }
            }
            if (dual) {
                frontStream?.lens?.let { lens ->
                    frontTrackingBox?.let { box ->
                        viewportSubject(box, lens, frontStreamSize, frames?.front)?.let(::add)
                    }
                }
            }
        }
        _trackedSubjects.value = subjects
    }

    private fun viewportSubject(
        sensorBox: FloatArray,
        lens: CameraHardware.Lens,
        streamSize: Size?,
        frame: NormalizedRect?
    ): TrackedSubject? {
        var left = sensorBox[0]
        var top = sensorBox[1]
        var right = sensorBox[2]
        var bottom = sensorBox[3]

        // Rotate into display space: each 90 degrees clockwise maps (x, y) -> (1 - y, x).
        val display = displayRotationFor(lens)
        repeat(display / 90) {
            val nl = 1f - bottom
            val nt = left
            val nr = 1f - top
            val nb = right
            left = nl; top = nt; right = nr; bottom = nb
        }
        if (lens.isFront) {
            val nl = 1f - right
            val nr = 1f - left
            left = nl
            right = nr
        }

        val dest = frame ?: NormalizedRect.FULL
        val destWidthPx = dest.width * previewWidth
        val destHeightPx = dest.height * previewHeight
        val size = streamSize
        if (size != null && destWidthPx > 0f && destHeightPx > 0f) {
            val (fx, fy) = CenterCrop.visibleFraction(
                size.width, size.height, textureRotationFor(lens), destWidthPx, destHeightPx
            )
            left = CenterCrop.toVisibleWindow(left, fx)
            right = CenterCrop.toVisibleWindow(right, fx)
            top = CenterCrop.toVisibleWindow(top, fy)
            bottom = CenterCrop.toVisibleWindow(bottom, fy)
        }

        // Fully cropped out of frame: nothing to draw.
        if (right <= 0f || left >= 1f || bottom <= 0f || top >= 1f) return null

        return TrackedSubject(
            bounds = NormalizedRect(
                dest.left + dest.width * left.coerceIn(0f, 1f),
                dest.top + dest.height * top.coerceIn(0f, 1f),
                dest.left + dest.width * right.coerceIn(0f, 1f),
                dest.top + dest.height * bottom.coerceIn(0f, 1f)
            ),
            score = 0,
            onFrontStream = lens.isFront
        )
    }

    // ---------------------------------------------------------------------------------------
    // Compositor layout
    // ---------------------------------------------------------------------------------------

    private fun pushLayout() {
        val dual = _cameraMode.value == CameraMode.DUAL_VLOG && frontStream != null
        val layout = _dualVlogLayout.value
        compositor.setLayout(
            CameraCompositor.LayoutState(
                dual = dual,
                frames = if (dual) {
                    DualVlogNormalizedGeometry.framesFor(
                        layout,
                        _isDualStreamSwapped.value,
                        _dualVlogPipRect.value
                    )
                } else {
                    null
                },
                circleInset = layout == DualVlogLayout.PIP_CIRCLE,
                profile = _colorProfile.value
            )
        )
    }

    // ---------------------------------------------------------------------------------------
    // Domain overrides
    // ---------------------------------------------------------------------------------------

    override fun setMode(mode: CameraMode) {
        val previous = _cameraMode.value
        super.setMode(mode)
        if (previous != mode) {
            // Leaving Dual Vlog must release the second camera before the next session opens.
            if (previous == CameraMode.DUAL_VLOG) compositor.releaseInputSurface(StreamSlot.FRONT)
            configureSession()
        }
    }

    override fun setLens(lens: LensFacing) {
        val previous = _currentLens.value
        super.setLens(lens)
        if (previous != lens) configureSession()
    }

    override fun setZoom(zoom: Float) {
        val previousLens = targetLens()?.cameraId
        super.setZoom(zoom)
        // Crossing an optical lens boundary needs a new session; digital zoom does not.
        if (targetLens()?.cameraId != previousLens) configureSession() else refreshRepeating()
    }

    override fun setFlash(flash: FlashMode) {
        super.setFlash(flash)
        refreshRepeating()
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        val previous = _aspectRatio.value
        super.setAspectRatio(ratio)
        if (aspectGroup(previous) != aspectGroup(ratio)) configureSession()
    }

    override fun setColorProfile(profile: ColorProfile) {
        super.setColorProfile(profile)
        pushLayout()
    }

    override fun setPhotoResolution(resolution: PhotoResolution) {
        val previous = _photoResolution.value
        super.setPhotoResolution(resolution)
        if (previous != resolution) configureSession()
    }

    override fun setVideoResolution(resolution: VideoResolution) {
        super.setVideoResolution(resolution)
    }

    override fun setVideoStabilizationEnabled(enabled: Boolean) {
        super.setVideoStabilizationEnabled(enabled)
        refreshRepeating()
    }

    override fun setDualVlogLayout(layout: DualVlogLayout) {
        super.setDualVlogLayout(layout)
        pushLayout()
    }

    override fun swapDualStreams() {
        super.swapDualStreams()
        pushLayout()
    }

    override fun setDualVlogPipRect(rect: NormalizedRect) {
        super.setDualVlogPipRect(rect)
        pushLayout()
    }

    override fun setSubjectTrackingEnabled(enabled: Boolean) {
        super.setSubjectTrackingEnabled(enabled)
        if (!enabled) {
            rearTrackingBox = null
            frontTrackingBox = null
        }
        refreshRepeating()
    }

    override fun setFocusPoint(x: Float, y: Float) {
        super.setFocusPoint(x, y)
        meteringPoint = x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
        // An explicit tap overrides whatever the tracker had latched onto.
        rearTrackingBox = null
        frontTrackingBox = null
        rearStream?.let { it.triggerAutoFocus(buildFrame(it.lens)) }
    }

    override fun clearFocusPoint() {
        super.clearFocusPoint()
        meteringPoint = null
        refreshRepeating()
    }

    override fun updateProSettings(transform: (ProSettings) -> ProSettings) {
        val previousAnalysis = wantsAnalysis()
        super.updateProSettings(transform)
        if (wantsAnalysis() != previousAnalysis) configureSession() else refreshRepeating()
        activeLens?.let { readCapabilities(it) }
    }

    // ---------------------------------------------------------------------------------------
    // Live analysis (histogram, focus peaking, zebra clipping)
    // ---------------------------------------------------------------------------------------

    /**
     * Camera2 hands us YUV_420_888 rather than CameraX's RGBA, so luminance comes straight from
     * the Y plane and chroma is sampled at half resolution to rebuild approximate RGB bins.
     */
    private fun processAnalysisFrame(image: android.media.Image) {
        try {
            val now = System.nanoTime()
            if (now - lastHistogramEmitNanos < HISTOGRAM_INTERVAL_NANOS) return
            lastHistogramEmitNanos = now

            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return

            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuffer: ByteBuffer = yPlane.buffer
            val uBuffer: ByteBuffer = uPlane.buffer
            val vBuffer: ByteBuffer = vPlane.buffer

            val rBins = histogramRed.also { it.fill(0) }
            val gBins = histogramGreen.also { it.fill(0) }
            val bBins = histogramBlue.also { it.fill(0) }
            val lumBins = histogramLuma.also { it.fill(0) }

            val gridWidth = width / MASK_STEP
            val gridHeight = height / MASK_STEP
            val trackGrid = gridWidth >= 3 && gridHeight >= 3
            if (trackGrid && (lumaGridWidth != gridWidth || lumaGridHeight != gridHeight)) {
                lumaGrid = ByteArray(gridWidth * gridHeight)
                lumaGridWidth = gridWidth
                lumaGridHeight = gridHeight
            }

            val step = 4
            var sampleCount = 0
            for (y in 0 until height step step) {
                val yRow = y * yPlane.rowStride
                val uvRow = (y / 2) * uPlane.rowStride
                for (x in 0 until width step step) {
                    val yIndex = yRow + x * yPlane.pixelStride
                    if (yIndex >= yBuffer.limit()) break
                    val luma = yBuffer.get(yIndex).toInt() and 0xFF

                    val uvIndex = uvRow + (x / 2) * uPlane.pixelStride
                    val u = if (uvIndex < uBuffer.limit()) (uBuffer.get(uvIndex).toInt() and 0xFF) - 128 else 0
                    val v = if (uvIndex < vBuffer.limit()) (vBuffer.get(uvIndex).toInt() and 0xFF) - 128 else 0

                    val r = (luma + 1.370705f * v).toInt().coerceIn(0, 255)
                    val g = (luma - 0.337633f * u - 0.698001f * v).toInt().coerceIn(0, 255)
                    val b = (luma + 1.732446f * u).toInt().coerceIn(0, 255)

                    rBins[r * BIN_COUNT / 256]++
                    gBins[g * BIN_COUNT / 256]++
                    bBins[b * BIN_COUNT / 256]++
                    lumBins[luma * BIN_COUNT / 256]++
                    sampleCount++
                }
            }

            if (sampleCount > 0) {
                val peak = max(max(rBins.max(), gBins.max()), max(bBins.max(), lumBins.max()))
                    .coerceAtLeast(1)
                _liveHistogram.value = HistogramData(
                    redBins = rBins.map { it * 100 / peak },
                    greenBins = gBins.map { it * 100 / peak },
                    blueBins = bBins.map { it * 100 / peak },
                    luminanceBins = lumBins.map { it * 100 / peak }
                )
            }

            if (trackGrid) updateExposureMask(yBuffer, yPlane.rowStride, yPlane.pixelStride, gridWidth, gridHeight)
        } catch (e: Exception) {
            Log.w(TAG, "Dropped analysis frame", e)
        }
    }

    /**
     * Focus peaking and zebra clipping both work purely on luminance, so they read the Y plane
     * directly instead of the interleaved RGBA the CameraX analyzer used to provide.
     */
    private fun updateExposureMask(
        yBuffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        gridWidth: Int,
        gridHeight: Int
    ) {
        val pro = _proSettings.value
        if (!pro.focusPeakingEnabled && !pro.zebraClippingEnabled) {
            if (!_exposureMask.value.isEmpty) _exposureMask.value = ExposureMask()
            return
        }

        for (gy in 0 until gridHeight) {
            val rowStart = gy * MASK_STEP * rowStride
            for (gx in 0 until gridWidth) {
                val index = rowStart + gx * MASK_STEP * pixelStride
                val luma = if (index < yBuffer.limit()) yBuffer.get(index).toInt() and 0xFF else 0
                lumaGrid[gy * gridWidth + gx] = (luma shr 1).toByte()
            }
        }

        val cells = gridWidth * gridHeight
        val peaking = if (pro.focusPeakingEnabled) ByteArray(cells) else ByteArray(0)
        val zebra = if (pro.zebraClippingEnabled) ByteArray(cells) else ByteArray(0)

        for (gy in 1 until gridHeight - 1) {
            for (gx in 1 until gridWidth - 1) {
                val index = gy * gridWidth + gx
                val centre = lumaGrid[index].toInt() and 0xFF
                if (zebra.isNotEmpty() && centre >= ZEBRA_THRESHOLD) zebra[index] = 1
                if (peaking.isNotEmpty()) {
                    val right = lumaGrid[index + 1].toInt() and 0xFF
                    val below = lumaGrid[index + gridWidth].toInt() and 0xFF
                    if (abs(centre - right) + abs(centre - below) >= PEAKING_THRESHOLD) {
                        peaking[index] = 1
                    }
                }
            }
        }

        _exposureMask.value = ExposureMask(gridWidth, gridHeight, peaking, zebra)
    }

    // ---------------------------------------------------------------------------------------
    // Gallery, EXIF and MediaStore (unchanged by the Camera2 migration)
    // ---------------------------------------------------------------------------------------

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


    // ---------------------------------------------------------------------------------------
    // Still capture
    // ---------------------------------------------------------------------------------------

    private fun mimeTypeFor(format: CaptureFormat) = when (format) {
        CaptureFormat.RAW_DNG, CaptureFormat.RAW_PLUS_JPEG -> "image/x-adobe-dng"
        else -> "image/jpeg"
    }

    /** JPEG EXIF orientation for the current device attitude, per the Camera2 contract. */
    private fun jpegOrientation(lens: CameraHardware.Lens): Int {
        val device = if (lens.isFront) -deviceOrientationDegrees else deviceOrientationDegrees
        return (lens.sensorOrientation + device + 360) % 360
    }

    override suspend fun capturePhoto(): CapturedMedia {
        val ctx = context
        val stream = rearStream
        val reader = stillReader
        val lens = activeLens
        if (ctx == null || stream == null || reader == null || lens == null || !stream.isReady) {
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
                Log.i(TAG, "RAW requested but no DNG writer; saving JPEG")
                CaptureFormat.JPEG
            } else {
                requestedFormat
            }
            if (effectiveFormat != requestedFormat) _captureFormat.value = effectiveFormat

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "PXL_$timeStamp.${effectiveFormat.extension}"
            val geoLocation =
                if (_geotaggingEnabled.value) locationProvider.lastKnownLocation() else null

            val bytes = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                awaitJpeg(reader) {
                    stream.captureStill(
                        target = reader.surface,
                        frame = buildFrame(lens),
                        jpegOrientation = jpegOrientation(lens),
                        jpegQuality = 100,
                        location = geoLocation?.toAndroidLocation(),
                        onFailed = { message -> Log.e(TAG, "Still capture failed: $message") }
                    )
                }
            } ?: throw IllegalStateException("Capture timed out")

            val uri = withContext(Dispatchers.IO) {
                MediaStoreWriter.writeImage(ctx, fileName, mimeTypeFor(effectiveFormat), bytes)
            } ?: throw IllegalStateException("Unable to save capture")

            val now = System.currentTimeMillis()
            val media = CapturedMedia(
                id = "IMG_$now",
                uri = uri.toString(),
                fileName = fileName,
                timestamp = now,
                width = reader.width,
                height = reader.height,
                format = effectiveFormat,
                mode = _cameraMode.value,
                exif = ComputationalPipeline.generateExif(
                    mode = _cameraMode.value,
                    lens = _currentLens.value,
                    zoom = _zoomRatio.value,
                    proSettings = _proSettings.value,
                    captureFormat = effectiveFormat,
                    ultraHdr = _ultraHdrEnabled.value,
                    capturedAtEpochMillis = now,
                    location = geoLocation
                )
            )

            _recentMedia.value = media
            _galleryList.value = listOf(media) + _galleryList.value
            _captureProgress.value =
                CaptureProgress(CaptureState.COMPLETE, 1.0f, "Saved to DCIM/AuraCam")
            delay(600)
            return media
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "Capture failed")
            delay(1200)
            throw e
        } finally {
            _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "")
            captureInFlight.set(false)
        }
    }

    private suspend fun awaitJpeg(reader: ImageReader, submit: () -> Unit): ByteArray =
        suspendCoroutine { continuation ->
            reader.setOnImageAvailableListener({ r ->
                val image = runCatching { r.acquireNextImage() }.getOrNull()
                    ?: return@setOnImageAvailableListener
                val bytes = try {
                    val buffer = image.planes[0].buffer
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                } finally {
                    image.close()
                    r.setOnImageAvailableListener(null, null)
                }
                continuation.resume(bytes)
            }, requireHandler())
            submit()
        }

    private fun GeoLocation.toAndroidLocation(): Location =
        Location(LocationManager.GPS_PROVIDER).also {
            it.latitude = latitude
            it.longitude = longitude
            altitudeMeters?.let { altitude -> it.altitude = altitude }
            accuracyMeters?.let { accuracy -> it.accuracy = accuracy }
        }

    // ---------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------

    /**
     * Recording resolution follows the viewfinder's aspect so the encoded frame is the composite
     * the user framed, not a differently-shaped crop of it.
     */
    private fun recordingSize(): Size {
        val res = _videoResolution.value
        val longEdge = max(res.width, res.height)
        val viewW = previewWidth.takeIf { it > 0 } ?: 1080
        val viewH = previewHeight.takeIf { it > 0 } ?: 1920
        return if (viewH >= viewW) {
            val width = (longEdge.toFloat() * viewW / viewH).roundToInt()
            Size(width and 1.inv(), longEdge and 1.inv())
        } else {
            val height = (longEdge.toFloat() * viewH / viewW).roundToInt()
            Size(longEdge and 1.inv(), height and 1.inv())
        }
    }

    private fun recordingBitRate(size: Size): Int =
        (size.width.toLong() * size.height * RECORD_BITS_PER_PIXEL / 1000L * 30L).toInt()
            .coerceIn(4_000_000, 48_000_000)

    override suspend fun toggleVideoRecording() {
        val ctx = context
        if (ctx == null || previewSurface == null) {
            super.toggleVideoRecording()
            return
        }

        if (_isRecording.value) {
            stopRecording()
            return
        }

        val size = recordingSize()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "VID_$timeStamp.mp4"
        val newRecorder = CompositeRecorder(ctx)
        val surface = withContext(Dispatchers.IO) {
            newRecorder.start(
                outputWidth = size.width,
                outputHeight = size.height,
                frameRate = 30,
                bitRate = recordingBitRate(size),
                displayName = fileName
            )
        }
        if (surface == null) {
            Log.e(TAG, "Unable to start recorder")
            _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "Recording unavailable")
            return
        }
        recorder = newRecorder
        compositor.setEncoderOutput(surface, size.width, size.height)
        _isRecording.value = true
        _recordingDurationSeconds.value = 0
        recordingTicker = coroutineScope.launch {
            while (isActive && _isRecording.value) {
                delay(250)
                _recordingDurationSeconds.value =
                    (newRecorder.recordedDurationMs / 1000L).toInt()
            }
        }
    }

    private suspend fun stopRecording() {
        val active = recorder ?: run {
            _isRecording.value = false
            return
        }
        recordingTicker?.cancel()
        recordingTicker = null
        compositor.setEncoderOutput(null, 0, 0)
        val result = withContext(Dispatchers.IO) { active.stop() }
        recorder = null
        _isRecording.value = false
        _recordingDurationSeconds.value = 0

        if (result == null) {
            Log.w(TAG, "Recording produced no output")
            return
        }
        val now = System.currentTimeMillis()
        val media = CapturedMedia(
            id = "VID_$now",
            uri = result.uri.toString(),
            fileName = result.fileName,
            timestamp = now,
            width = result.width,
            height = result.height,
            format = CaptureFormat.JPEG,
            mode = _cameraMode.value,
            exif = ExifInfo(
                deviceModel = Build.MANUFACTURER + " " + Build.MODEL,
                lensFocalLength = "",
                iso = 0,
                shutterSpeed = "",
                aperture = "",
                exposureBias = "",
                whiteBalance = "",
                format = "video/mp4",
                resolution = "${result.width} × ${result.height}",
                timestamp = ComputationalPipeline.formatTimestamp(now)
            )
        )
        _recentMedia.value = media
        _galleryList.value = listOf(media) + _galleryList.value
    }

    private fun hasPermission(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun closeStreams() {
        rearStream?.close()
        rearStream = null
        frontStream?.close()
        frontStream = null
        runCatching { stillReader?.close() }
        stillReader = null
        runCatching { analysisReader?.close() }
        analysisReader = null
    }

    override fun release() {
        if (released) return
        released = true
        recordingTicker?.cancel()
        recordingTicker = null
        runCatching { recorder?.stop() }
        recorder = null
        orientationListener?.disable()
        orientationListener = null
        sensorLeveler.stop()
        closeStreams()
        compositor.stop()
        sessionThread?.quitSafely()
        sessionThread = null
        sessionHandler = null
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
        previewSurface = null
        hardware = null
        context = null
        sessionSignature = null
        super.release()
    }

    private val canWriteDng: Boolean
        get() = activeLens?.rawSupported == true && DNG_WRITER_IMPLEMENTED

    private companion object {
        const val BIN_COUNT = 32
        const val DNG_WRITER_IMPLEMENTED = false
        const val HISTOGRAM_INTERVAL_NANOS = 100_000_000L
        const val TRACKING_INTERVAL_NANOS = 100_000_000L
        const val TRACKING_DEADZONE = 0.02f
        const val MASK_STEP = 8
        const val ZEBRA_THRESHOLD = 246
        const val PEAKING_THRESHOLD = 28
        const val CAPTURE_TIMEOUT_MS = 8_000L
        const val RECORD_BITS_PER_PIXEL = 130L
    }
}
