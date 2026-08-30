package com.auracam.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun rememberMediaImage(uri: String, maxDimension: Int = 2048): ImageBitmap?
