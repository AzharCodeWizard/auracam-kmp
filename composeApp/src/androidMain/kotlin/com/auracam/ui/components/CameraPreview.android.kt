package com.auracam.ui.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.auracam.camera.domain.CameraEngine
import com.auracam.camera.domain.PlatformCameraEngine

/**
 * The viewfinder is a single [SurfaceView] that the engine's GL compositor renders into.
 *
 * There is deliberately only one of these, even in Dual Vlog: both camera streams are composited
 * into this one surface. That is what keeps layout changes and stream swaps from tearing down and
 * rebuilding camera sessions.
 */
@Composable
actual fun CameraPreview(
    engine: CameraEngine,
    modifier: Modifier
) {
    val context = LocalContext.current
    val platformEngine = engine as? PlatformCameraEngine

    val surfaceView = remember {
        SurfaceView(context).apply { keepScreenOn = true }
    }

    DisposableEffect(platformEngine, surfaceView) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                platformEngine?.attachPreview(
                    context = context,
                    surface = holder.surface,
                    width = width,
                    height = height,
                    rotation = surfaceView.display?.rotation ?: 0
                )
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                platformEngine?.detachPreview()
            }
        }
        surfaceView.holder.addCallback(callback)
        onDispose {
            surfaceView.holder.removeCallback(callback)
            platformEngine?.detachPreview()
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier.fillMaxSize()
    )
}
