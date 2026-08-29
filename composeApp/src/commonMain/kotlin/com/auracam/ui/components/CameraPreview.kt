package com.auracam.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.auracam.camera.domain.CameraEngine

@Composable
expect fun CameraPreview(
    engine: CameraEngine,
    modifier: Modifier = Modifier
)
