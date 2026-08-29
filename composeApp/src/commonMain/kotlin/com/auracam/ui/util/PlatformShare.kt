package com.auracam.ui.util

import androidx.compose.runtime.Composable
import com.auracam.camera.domain.CapturedMedia

@Composable
expect fun rememberPlatformShare(): (CapturedMedia) -> Unit
