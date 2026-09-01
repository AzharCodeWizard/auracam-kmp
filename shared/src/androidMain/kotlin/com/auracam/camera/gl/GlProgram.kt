package com.auracam.camera.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws a camera stream (an external OES texture) into an arbitrary rectangle of the output,
 * applying orientation, center-crop, optional mirroring, and a 4x5 tone-filter colour matrix.
 *
 * The colour matrix is the same [com.auracam.processing.ToneFilterMatrix] data the UI uses, so a
 * filter looks identical in the viewfinder and in the recorded file — it is baked into the pixels
 * once, here, instead of being a Compose overlay that recording cannot see.
 */
internal class GlProgram {

    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var uColorMatrix = 0
    private var uColorOffset = 0
    private var uMaskCircle = 0
    private var uTexture = 0

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val texQuad: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .also {
            it.put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
            it.position(0)
        }

    private val texMatrix = FloatArray(16)
    private val scratchA = FloatArray(16)
    private val scratchB = FloatArray(16)

    fun create() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uColorMatrix = GLES20.glGetUniformLocation(program, "uColorMatrix")
        uColorOffset = GLES20.glGetUniformLocation(program, "uColorOffset")
        uMaskCircle = GLES20.glGetUniformLocation(program, "uMaskCircle")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
    }

    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    /**
     * @param dest destination rect in normalized viewport coords (0..1, origin top-left)
     * @param stMatrix the SurfaceTexture transform for the current frame
     * @param rotationDegrees clockwise rotation needed to display the stream upright
     * @param mirror horizontal flip, for front-facing streams
     */
    @Suppress("LongParameterList")
    fun draw(
        textureId: Int,
        stMatrix: FloatArray,
        dest: FloatArray,
        outputWidth: Int,
        outputHeight: Int,
        /** Source dimensions as they appear *after* the SurfaceTexture transform. */
        displaySourceWidth: Int,
        displaySourceHeight: Int,
        /** Rotation still needed on top of whatever the SurfaceTexture transform already applies. */
        rotationDegrees: Int,
        /** True when crop axes must be swapped to land on the right axis in pre-transform space. */
        swapCropAxes: Boolean,
        mirror: Boolean,
        colorMatrix: FloatArray,
        colorOffset: FloatArray,
        circleMask: Boolean
    ) {
        if (program == 0 || outputWidth <= 0 || outputHeight <= 0) return

        val left = dest[0]
        val top = dest[1]
        val right = dest[2]
        val bottom = dest[3]
        if (right <= left || bottom <= top) return

        // Normalized top-left rect -> NDC quad (y flipped).
        val x0 = left * 2f - 1f
        val x1 = right * 2f - 1f
        val y0 = 1f - bottom * 2f
        val y1 = 1f - top * 2f
        quad.position(0)
        quad.put(floatArrayOf(x0, y0, x1, y0, x0, y1, x1, y1))
        quad.position(0)

        buildTexMatrix(
            stMatrix = stMatrix,
            destWidthPx = (right - left) * outputWidth,
            destHeightPx = (bottom - top) * outputHeight,
            displaySourceWidth = displaySourceWidth,
            displaySourceHeight = displaySourceHeight,
            rotationDegrees = rotationDegrees,
            swapCropAxes = swapCropAxes,
            mirror = mirror
        )

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glUniformMatrix4fv(uColorMatrix, 1, false, colorMatrix, 0)
        GLES20.glUniform4fv(uColorOffset, 1, colorOffset, 0)
        GLES20.glUniform1f(uMaskCircle, if (circleMask) 1f else 0f)

        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        texQuad.position(0)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texQuad)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    /**
     * texMatrix = ST * T(+0.5) * Mirror * R(rotation) * S(crop) * T(-0.5)
     *
     * The SurfaceTexture transform is the outermost factor, so anything this method does happens
     * in the buffer's own coordinate space and must account for axis swaps that transform makes.
     *
     * Texture coordinates are transformed rather than vertices, which keeps the destination quad
     * pixel-exact regardless of how the source is rotated or cropped.
     */
    private fun buildTexMatrix(
        stMatrix: FloatArray,
        destWidthPx: Float,
        destHeightPx: Float,
        displaySourceWidth: Int,
        displaySourceHeight: Int,
        rotationDegrees: Int,
        swapCropAxes: Boolean,
        mirror: Boolean
    ) {
        // Crop is decided in display space, where the source dimensions are already correct.
        val (cropX, cropY) = CenterCrop.visibleFraction(
            displaySourceWidth, displaySourceHeight, 0, destWidthPx, destHeightPx
        )

        // ...but it is *applied* in pre-transform texture space, so the axes swap whenever the
        // SurfaceTexture transform (or our own rotation) exchanges them.
        val scaleX = if (swapCropAxes) cropY else cropX
        val scaleY = if (swapCropAxes) cropX else cropY

        Matrix.setIdentityM(scratchA, 0)
        Matrix.translateM(scratchA, 0, 0.5f, 0.5f, 0f)
        if (mirror) Matrix.scaleM(scratchA, 0, -1f, 1f, 1f)
        Matrix.rotateM(scratchA, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(scratchA, 0, scaleX, scaleY, 1f)
        Matrix.translateM(scratchA, 0, -0.5f, -0.5f, 0f)

        Matrix.multiplyMM(scratchB, 0, stMatrix, 0, scratchA, 0)
        System.arraycopy(scratchB, 0, texMatrix, 0, 16)
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            varying vec2 vLocal;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
                vLocal = aTexCoord.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            varying vec2 vLocal;
            uniform samplerExternalOES uTexture;
            uniform mat4 uColorMatrix;
            uniform vec4 uColorOffset;
            uniform float uMaskCircle;
            void main() {
                if (uMaskCircle > 0.5) {
                    vec2 d = vLocal - vec2(0.5, 0.5);
                    if (dot(d, d) > 0.25) discard;
                }
                vec4 c = texture2D(uTexture, vTexCoord);
                vec4 graded = uColorMatrix * vec4(c.rgb, 1.0) + uColorOffset;
                gl_FragColor = vec4(clamp(graded.rgb, 0.0, 1.0), c.a);
            }
        """

        fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
            val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
            val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) {
                "GL program link failed: ${GLES20.glGetProgramInfoLog(program)}"
            }
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return program
        }

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) {
                "GL shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}"
            }
            return shader
        }
    }
}
