package com.auracam.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.auracam.camera.domain.CameraMode
import com.auracam.camera.domain.CaptureProgress
import com.auracam.camera.domain.CapturedMedia

/**
 * Pixel M3 Expressive Shutter Row Composable.
 * Delegates to [ShutterControlRow] in ViewfinderControls.kt.
 */
@Composable
fun ShutterRow(
    mode: CameraMode,
    isRecording: Boolean,
    recordingDurationSeconds: Int,
    captureProgress: CaptureProgress,
    recentMedia: CapturedMedia?,
    onShutterClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShutterControlRow(
        mode = mode,
        isRecording = isRecording,
        recordingDurationSeconds = recordingDurationSeconds,
        captureProgress = captureProgress,
        recentMedia = recentMedia,
        onShutterClick = onShutterClick,
        onFlipCamera = onFlipCamera,
        onGalleryClick = onGalleryClick,
        modifier = modifier
    )
}
