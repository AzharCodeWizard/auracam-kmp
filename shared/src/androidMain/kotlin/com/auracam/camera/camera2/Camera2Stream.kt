package com.auracam.camera.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.util.Log
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One open [CameraDevice] and its repeating capture session.
 *
 * Single-camera modes run one of these; Dual Vlog runs two, one per physical camera, whose ids
 * come from [CameraHardware.concurrentPair] so the HAL guarantees they can stream together.
 */
internal class Camera2Stream(
    context: Context,
    val lens: CameraHardware.Lens,
    private val handler: Handler
) {
    private val manager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    val tuner = CaptureRequestTuner(lens)

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var targets: List<Surface> = emptyList()
    private var repeatingTargets: List<Surface> = emptyList()
    private var lastFrame: CaptureRequestTuner.Frame? = null

    @Volatile
    private var closed = false

    /**
     * Released once the HAL has actually let go of the device.
     *
     * `CameraDevice.close()` returns before the camera is free, so opening the next session
     * immediately races the teardown and the HAL answers with ERROR_MAX_CAMERAS_IN_USE. Waiting
     * on this latch is what makes mode and lens switches reliable.
     */
    @Volatile
    private var closeLatch: CountDownLatch? = null

    val isReady: Boolean get() = session != null && device != null

    /** Called on the camera handler for every completed repeating frame. */
    var onResult: ((TotalCaptureResult) -> Unit)? = null

    /**
     * Opens the camera device only.
     *
     * Opening is deliberately separate from session configuration: for concurrent (Dual Vlog)
     * operation the camera service must see both devices opened *before* either session is
     * configured, otherwise it treats the first camera as an exclusive client and rejects the
     * second open with ERROR_MAX_CAMERAS_IN_USE.
     */
    @SuppressLint("MissingPermission")
    fun openDevice(onOpened: () -> Unit, onError: (String) -> Unit) {
        closed = false
        try {
            manager.openCamera(
                lens.cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (closed) {
                            camera.close()
                            return
                        }
                        device = camera
                        closeLatch = CountDownLatch(1)
                        onOpened()
                    }

                    override fun onClosed(camera: CameraDevice) {
                        closeLatch?.countDown()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.w(CAMERA2_TAG, "Camera ${lens.cameraId} disconnected")
                        camera.close()
                        if (device === camera) device = null
                        closeLatch?.countDown()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(CAMERA2_TAG, "Camera ${lens.cameraId} error $error")
                        camera.close()
                        if (device === camera) device = null
                        closeLatch?.countDown()
                        onError(describeError(error))
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(CAMERA2_TAG, "openCamera(${lens.cameraId}) failed", e)
            onError(e.message ?: "Unable to open camera")
        }
    }

    /**
     * Configures the capture session whose repeating request targets [repeating]; [extra]
     * surfaces (still-capture readers) are configured but not driven by the preview request.
     */
    fun configure(
        repeating: List<Surface>,
        extra: List<Surface> = emptyList(),
        frame: CaptureRequestTuner.Frame,
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) {
        val camera = device ?: return onError("Camera ${lens.cameraId} is not open")
        repeatingTargets = repeating
        targets = repeating + extra
        lastFrame = frame
        if (targets.isEmpty()) {
            onError("No output surfaces for camera ${lens.cameraId}")
            return
        }
        createSession(camera, frame, onReady, onError)
    }

    @Suppress("DEPRECATION")
    private fun createSession(
        camera: CameraDevice,
        frame: CaptureRequestTuner.Frame,
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            camera.createCaptureSession(
                targets,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        if (closed) {
                            configured.close()
                            return
                        }
                        session = configured
                        setRepeating(frame)
                        onReady()
                    }

                    override fun onConfigureFailed(configured: CameraCaptureSession) {
                        Log.e(CAMERA2_TAG, "Session config failed for ${lens.cameraId}")
                        onError("Camera configuration unsupported")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(CAMERA2_TAG, "createCaptureSession(${lens.cameraId}) failed", e)
            onError(e.message ?: "Unable to configure camera")
        }
    }

    /** Rebuilds and re-submits the repeating request with the current settings. */
    fun setRepeating(frame: CaptureRequestTuner.Frame) {
        lastFrame = frame
        val camera = device ?: return
        val active = session ?: return
        try {
            val template =
                if (frame.isVideo) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
            val builder = camera.createCaptureRequest(template)
            repeatingTargets.forEach(builder::addTarget)
            tuner.applyTo(builder, frame)
            active.setRepeatingRequest(builder.build(), captureCallback, handler)
        } catch (e: Exception) {
            Log.w(CAMERA2_TAG, "setRepeatingRequest(${lens.cameraId}) rejected", e)
        }
    }

    /**
     * Runs a one-shot AF/AE trigger so a tap-to-focus converges instead of waiting for the
     * continuous algorithm to drift onto the new region.
     */
    fun triggerAutoFocus(frame: CaptureRequestTuner.Frame) {
        val camera = device ?: return
        val active = session ?: return
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            repeatingTargets.forEach(builder::addTarget)
            tuner.applyTo(builder, frame)
            builder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                android.hardware.camera2.CameraMetadata.CONTROL_AF_TRIGGER_START
            )
            builder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                android.hardware.camera2.CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            active.capture(builder.build(), null, handler)
            // Leave the continuous request in place with the new regions.
            setRepeating(frame)
        } catch (e: Exception) {
            Log.w(CAMERA2_TAG, "AF trigger rejected", e)
        }
    }

    /**
     * Issues a still capture into [target], applying the same tuning as the preview so what you
     * see is what gets written.
     */
    fun captureStill(
        target: Surface,
        frame: CaptureRequestTuner.Frame,
        jpegOrientation: Int,
        jpegQuality: Int,
        location: android.location.Location?,
        onFailed: (String) -> Unit
    ) {
        val camera = device ?: return onFailed("Camera not open")
        val active = session ?: return onFailed("Camera session not ready")
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(target)
            tuner.applyTo(builder, frame)
            builder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
            builder.set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
            location?.let { builder.set(CaptureRequest.JPEG_GPS_LOCATION, it) }
            active.capture(
                builder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        s: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        onFailed("Capture failed (reason ${failure.reason})")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(CAMERA2_TAG, "Still capture rejected", e)
            onFailed(e.message ?: "Capture failed")
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            s: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            onResult?.invoke(result)
        }
    }

    /**
     * Closes the session and device, blocking briefly until the HAL confirms the device is free.
     * Must not be called from the camera handler thread, which delivers the confirmation.
     */
    fun close() {
        closed = true
        onResult = null
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        session = null
        val latch = closeLatch
        val hadDevice = device != null
        runCatching { device?.close() }
        device = null
        if (hadDevice && latch != null) {
            if (!latch.await(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(CAMERA2_TAG, "Camera ${lens.cameraId} did not confirm close in time")
            }
        }
        closeLatch = null
        targets = emptyList()
        repeatingTargets = emptyList()
        lastFrame = null
    }

    private fun describeError(error: Int): String = when (error) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "Camera already in use"
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "Too many cameras in use"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "Camera disabled by policy"
        else -> "Camera error $error"
    }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 1500L
    }
}
