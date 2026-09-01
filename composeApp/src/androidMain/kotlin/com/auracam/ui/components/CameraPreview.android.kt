package com.auracam.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.auracam.camera.domain.CameraEngine
import com.auracam.camera.domain.PlatformCameraEngine

@Composable
actual fun CameraPreview(
    engine: CameraEngine,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(lifecycleOwner, engine) {
        if (engine is PlatformCameraEngine) {
            engine.bindToLifecycle(context, lifecycleOwner, previewView)
        }
        onDispose { }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
actual fun SecondaryCameraPreview(
    engine: CameraEngine,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val secondaryPreviewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(lifecycleOwner, engine) {
        if (engine is PlatformCameraEngine) {
            engine.bindSecondaryPreview(secondaryPreviewView)
        }
        onDispose {
            if (engine is PlatformCameraEngine) {
                engine.unbindSecondaryPreview()
            }
        }
    }

    AndroidView(
        factory = { secondaryPreviewView },
        modifier = modifier.fillMaxSize()
    )
}
