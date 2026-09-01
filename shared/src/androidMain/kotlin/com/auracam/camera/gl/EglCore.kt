package com.auracam.camera.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * Minimal EGL 1.4 setup shared by every output the compositor draws to.
 *
 * One context is created up front and reused for the preview surface and the encoder surface, so
 * both are drawn from the same GL state and the same camera textures within a single frame.
 */
internal class EglCore {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null

    /**
     * A 1x1 pbuffer kept current whenever no output surface is bound.
     *
     * GL calls — including SurfaceTexture.updateTexImage — need *some* current surface, and the
     * camera textures are updated before we know which output we are about to draw into.
     */
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    val isReady: Boolean get() = context != EGL14.EGL_NO_CONTEXT

    fun setup() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0
        ) { "eglChooseConfig failed" }
        config = configs[0]

        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        pbuffer = EGL14.eglCreatePbufferSurface(
            display,
            config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0
        )
        check(pbuffer != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        check(makeCurrent(pbuffer)) { "eglMakeCurrent(pbuffer) failed" }
    }

    /** Binds the fallback pbuffer so GL calls are legal outside a draw pass. */
    fun makeCurrentOffscreen(): Boolean =
        pbuffer != EGL14.EGL_NO_SURFACE && makeCurrent(pbuffer)

    fun createWindowSurface(surface: Surface): EGLSurface? = runCatching {
        EGL14.eglCreateWindowSurface(
            display,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        ).takeIf { it != EGL14.EGL_NO_SURFACE }
    }.getOrNull()

    fun makeCurrent(eglSurface: EGLSurface): Boolean =
        EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)

    fun swapBuffers(eglSurface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, eglSurface)

    fun setPresentationTime(eglSurface: EGLSurface, nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, nanos)
    }

    fun releaseSurface(eglSurface: EGLSurface?) {
        if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, eglSurface)
        }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            releaseSurface(pbuffer)
            pbuffer = EGL14.EGL_NO_SURFACE
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        config = null
    }

    private companion object {
        /** EGL_RECORDABLE_ANDROID — required for surfaces consumed by MediaCodec. */
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
