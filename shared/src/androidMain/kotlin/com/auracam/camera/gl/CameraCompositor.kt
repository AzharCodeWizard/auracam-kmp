package com.auracam.camera.gl

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.auracam.camera.domain.ColorProfile
import com.auracam.camera.domain.DualVlogFrames
import com.auracam.processing.ToneFilterMatrix
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val GL_TAG = "AuraCamGL"

/** Camera streams the compositor can draw. Slot order matches [DualVlogFrames]. */
internal enum class StreamSlot { REAR, FRONT }

/**
 * Owns the GL thread that turns one or two live camera textures into the frames the user sees and
 * the frames that get encoded.
 *
 * Both outputs are drawn from the same textures in the same pass, which is what makes
 * "record the exact visual layout" true by construction rather than by re-implementing the
 * layout in two places. It also means Split/PiP/Swap are uniform changes — no camera rebinding,
 * which is what previously killed a feed on every swap.
 */
internal class CameraCompositor {

    /** Immutable snapshot of everything the renderer needs for one frame. */
    data class LayoutState(
        val dual: Boolean,
        val frames: DualVlogFrames?,
        val circleInset: Boolean,
        val profile: ColorProfile
    )

    private class Stream {
        var textureId = 0
        var surfaceTexture: SurfaceTexture? = null
        var surface: Surface? = null
        var width = 0
        var height = 0
        var rotation = 0
        var mirror = false
        var active = false
        var hasFrame = false
        var pending = false
        val stMatrix = FloatArray(16)

        /**
         * True when the SurfaceTexture transform exchanges the u and v axes, i.e. the HAL has
         * already baked the sensor rotation into the buffer transform. Detected from the live
         * matrix rather than assumed, because it differs between devices.
         */
        var stSwapsAxes = false

        /**
         * True when the SurfaceTexture transform already mirrors the image.
         *
         * A normal transform contains only the GL vertical flip (a single reflection). A front
         * camera whose HAL pre-mirrors the buffer contributes a second reflection, flipping the
         * handedness back — so mirroring again here would undo the selfie view.
         */
        var stMirrors = false
        var timestamp = 0L
    }

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val egl = EglCore()
    private val program = GlProgram()
    private val streams = mapOf(StreamSlot.REAR to Stream(), StreamSlot.FRONT to Stream())

    private var previewSurface: Surface? = null
    private var previewEgl: EGLSurface? = null
    private var previewWidth = 0
    private var previewHeight = 0

    private var encoderSurface: Surface? = null
    private var encoderEgl: EGLSurface? = null
    private var encoderWidth = 0
    private var encoderHeight = 0

    @Volatile
    private var layout = LayoutState(dual = false, frames = null, circleInset = false, profile = ColorProfile.NATURAL)

    private val colorMatrix = FloatArray(16)
    private val colorOffset = FloatArray(4)

    private val fullFrame = floatArrayOf(0f, 0f, 1f, 1f)
    private val rearRect = FloatArray(4)
    private val frontRect = FloatArray(4)

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        val t = HandlerThread("AuraCamGL").also { it.start() }
        thread = t
        handler = Handler(t.looper)
        post {
            runCatching {
                egl.setup()
                program.create()
                streams.values.forEach { it.textureId = createOesTexture() }
            }.onFailure { Log.e(GL_TAG, "GL init failed: ${it.message}", it) }
        }
        applyProfile(layout.profile)
    }

    fun stop() {
        if (!running) return
        running = false
        val h = handler
        val latch = CountDownLatch(1)
        h?.post {
            runCatching {
                streams.values.forEach { stream ->
                    stream.surface?.release()
                    stream.surfaceTexture?.setOnFrameAvailableListener(null)
                    stream.surfaceTexture?.release()
                    stream.surface = null
                    stream.surfaceTexture = null
                    stream.active = false
                    stream.hasFrame = false
                    if (stream.textureId != 0) {
                        GLES20.glDeleteTextures(1, intArrayOf(stream.textureId), 0)
                        stream.textureId = 0
                    }
                }
                egl.releaseSurface(previewEgl)
                egl.releaseSurface(encoderEgl)
                previewEgl = null
                encoderEgl = null
                program.release()
                egl.release()
            }
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
        thread?.quitSafely()
        thread = null
        handler = null
        previewSurface = null
        encoderSurface = null
    }

    /**
     * Creates (or resizes) the camera-facing [Surface] for [slot] and blocks until it exists,
     * because the caller needs it to configure the capture session.
     */
    fun acquireInputSurface(
        slot: StreamSlot,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        mirror: Boolean
    ): Surface? {
        val h = handler ?: return null
        var result: Surface? = null
        val latch = CountDownLatch(1)
        h.post {
            runCatching {
                egl.makeCurrentOffscreen()
                val stream = streams.getValue(slot)
                if (stream.textureId == 0) stream.textureId = createOesTexture()
                if (stream.surfaceTexture == null) {
                    val st = SurfaceTexture(stream.textureId)
                    st.setOnFrameAvailableListener({
                        stream.pending = true
                        requestRender()
                    }, h)
                    stream.surfaceTexture = st
                    stream.surface = Surface(st)
                }
                stream.surfaceTexture?.setDefaultBufferSize(width, height)
                stream.width = width
                stream.height = height
                stream.rotation = ((rotationDegrees % 360) + 360) % 360
                stream.mirror = mirror
                stream.active = true
                stream.hasFrame = false
                result = stream.surface
            }.onFailure { Log.e(GL_TAG, "acquireInputSurface($slot) failed", it) }
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)
        return result
    }

    fun releaseInputSurface(slot: StreamSlot) = post {
        val stream = streams.getValue(slot)
        stream.active = false
        stream.hasFrame = false
        stream.pending = false
    }

    fun setPreviewOutput(surface: Surface?, width: Int, height: Int) = post {
        if (previewSurface !== surface) {
            egl.releaseSurface(previewEgl)
            previewEgl = null
            previewSurface = surface
            if (surface != null && surface.isValid) {
                previewEgl = egl.createWindowSurface(surface)
            }
        }
        previewWidth = width
        previewHeight = height
        requestRender()
    }

    fun setEncoderOutput(surface: Surface?, width: Int, height: Int) = post {
        egl.releaseSurface(encoderEgl)
        encoderEgl = null
        encoderSurface = surface
        encoderWidth = width
        encoderHeight = height
        if (surface != null && surface.isValid) {
            encoderEgl = egl.createWindowSurface(surface)
        }
    }

    fun setLayout(state: LayoutState) {
        layout = state
        applyProfile(state.profile)
        requestRender()
    }

    fun updateStreamOrientation(slot: StreamSlot, rotationDegrees: Int, mirror: Boolean) = post {
        val stream = streams.getValue(slot)
        stream.rotation = ((rotationDegrees % 360) + 360) % 360
        stream.mirror = mirror
        requestRender()
    }

    /**
     * The 4x5 matrix the UI uses is row-major with 0..255 offsets; GL wants a column-major mat4
     * plus a 0..1 offset vector.
     */
    private fun applyProfile(profile: ColorProfile) {
        val m = ToneFilterMatrix.colorMatrixFor(profile)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                colorMatrix[col * 4 + row] = m[row * 5 + col]
            }
            colorOffset[row] = m[row * 5 + 4] / 255f
        }
    }

    private fun post(block: () -> Unit) {
        handler?.post {
            if (running) runCatching(block).onFailure { Log.w(GL_TAG, "GL task failed", it) }
        }
    }

    private fun requestRender() {
        val h = handler ?: return
        h.removeCallbacks(renderRunnable)
        h.post(renderRunnable)
    }

    private val renderRunnable = Runnable { runCatching { render() }.onFailure { Log.w(GL_TAG, "render failed", it) } }

    private fun render() {
        if (!running || !egl.isReady) return

        // Texture updates need a current context even though no output is bound yet.
        egl.makeCurrentOffscreen()
        streams.values.forEach { stream ->
            val st = stream.surfaceTexture ?: return@forEach
            if (stream.pending) {
                stream.pending = false
                st.updateTexImage()
                st.getTransformMatrix(stream.stMatrix)
                stream.stSwapsAxes = kotlin.math.abs(stream.stMatrix[0]) < 0.5f
                // Determinant of the transform's 2x2 linear part.
                val det = stream.stMatrix[0] * stream.stMatrix[5] -
                    stream.stMatrix[4] * stream.stMatrix[1]
                stream.stMirrors = det > 0f
                stream.timestamp = st.timestamp
                if (!stream.hasFrame) {
                    Log.i(
                        GL_TAG,
                        "stMatrix[${stream.width}x${stream.height} rot=${stream.rotation} " +
                            "mirror=${stream.mirror}] = ${stream.stMatrix.joinToString(",") { "%.2f".format(it) }}"
                    )
                }
                stream.hasFrame = true
            }
        }

        val rear = streams.getValue(StreamSlot.REAR)
        val front = streams.getValue(StreamSlot.FRONT)
        if (!rear.hasFrame && !front.hasFrame) return

        val state = layout
        resolveRects(state)

        previewEgl?.let { target ->
            if (egl.makeCurrent(target)) {
                drawScene(state, previewWidth, previewHeight)
                egl.swapBuffers(target)
            }
        }

        encoderEgl?.let { target ->
            if (egl.makeCurrent(target)) {
                drawScene(state, encoderWidth, encoderHeight)
                val ts = if (rear.hasFrame) rear.timestamp else front.timestamp
                egl.setPresentationTime(target, ts)
                egl.swapBuffers(target)
            }
        }
    }

    private fun resolveRects(state: LayoutState) {
        val frames = state.frames
        if (!state.dual || frames == null) {
            fullFrame.copyInto(rearRect)
            fullFrame.copyInto(frontRect)
            return
        }
        rearRect[0] = frames.rear.left
        rearRect[1] = frames.rear.top
        rearRect[2] = frames.rear.right
        rearRect[3] = frames.rear.bottom
        frontRect[0] = frames.front.left
        frontRect[1] = frames.front.top
        frontRect[2] = frames.front.right
        frontRect[3] = frames.front.bottom
    }

    private fun drawScene(state: LayoutState, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val rear = streams.getValue(StreamSlot.REAR)
        val front = streams.getValue(StreamSlot.FRONT)

        if (!state.dual) {
            if (rear.hasFrame && rear.active) drawStream(rear, fullFrame, width, height, false)
            else if (front.hasFrame && front.active) drawStream(front, fullFrame, width, height, false)
            return
        }

        val frames = state.frames
        val insetIsFront = frames?.frontIsMain != true
        val overlay = state.circleInset

        // Main frame first so the inset composites on top of it in PiP layouts.
        if (insetIsFront) {
            if (rear.hasFrame) drawStream(rear, rearRect, width, height, false)
            if (front.hasFrame) drawStream(front, frontRect, width, height, overlay)
        } else {
            if (front.hasFrame) drawStream(front, frontRect, width, height, false)
            if (rear.hasFrame) drawStream(rear, rearRect, width, height, overlay)
        }
    }

    private fun drawStream(
        stream: Stream,
        rect: FloatArray,
        width: Int,
        height: Int,
        circleMask: Boolean
    ) {
        // Whatever rotation the SurfaceTexture transform already applies is subtracted here, so
        // a HAL that pre-rotates its buffers is not rotated a second time.
        val stRotation = if (stream.stSwapsAxes) 90 else 0
        val extraRotation = ((stream.rotation - stRotation) % 360 + 360) % 360
        val displayW = if (stream.stSwapsAxes) stream.height else stream.width
        val displayH = if (stream.stSwapsAxes) stream.width else stream.height
        val rotatedByUs = extraRotation == 90 || extraRotation == 270

        program.draw(
            textureId = stream.textureId,
            stMatrix = stream.stMatrix,
            dest = rect,
            outputWidth = width,
            outputHeight = height,
            displaySourceWidth = if (rotatedByUs) displayH else displayW,
            displaySourceHeight = if (rotatedByUs) displayW else displayH,
            rotationDegrees = extraRotation,
            swapCropAxes = stream.stSwapsAxes != rotatedByUs,
            mirror = stream.mirror != stream.stMirrors,
            colorMatrix = colorMatrix,
            colorOffset = colorOffset,
            circleMask = circleMask
        )
    }

    private fun createOesTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return ids[0]
    }
}

private fun FloatArray.copyInto(target: FloatArray) {
    System.arraycopy(this, 0, target, 0, size)
}
