package com.auracam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.auracam.camera.domain.CameraEngine

@Composable
actual fun CameraPreview(
    engine: CameraEngine,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E242B), Color(0xFF0F1216))
                )
            )
    )
}

@Composable
actual fun SecondaryCameraPreview(
    engine: CameraEngine,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2C3E50), Color(0xFF141A20))
                )
            )
    )
}
