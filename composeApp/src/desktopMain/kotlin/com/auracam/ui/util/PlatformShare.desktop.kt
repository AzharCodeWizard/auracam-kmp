package com.auracam.ui.util

import androidx.compose.runtime.Composable
import com.auracam.camera.domain.CapturedMedia

@Composable
actual fun rememberPlatformShare(): ((CapturedMedia) -> Unit)? = null
