package com.auracam.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.auracam.camera.domain.*
import com.auracam.ui.theme.PixelFocusPeakingGreen

/**
 * Composite Viewfinder Overlay assembling:
 * - High-contrast framing grids
 * - Focus peaking outline
 * - Dual-axis 3D leveler
 * - Floating top status bar & quick settings
 * - Video recording HUD
 * - Countdown timer overlay
 * - Computational capture banner
 */
@Composable
fun ViewfinderOverlay(
    mode: CameraMode,
    flashMode: FlashMode,
    captureFormat: CaptureFormat,
    colorProfile: ColorProfile,
    ultraHdr: Boolean,
    watermarkEnabled: Boolean,
    timerDuration: TimerDuration,
    gridType: GridType,
    proSettings: ProSettings,
    leveler: HorizonLeveler,
    isRecording: Boolean,
    recordingDurationSeconds: Int,
    captureProgress: CaptureProgress,
    onOpenQuickSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. High-Contrast Framing Grids
        FramingGridOverlay(
            gridType = gridType,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Focus Peaking Neon Green Highlight in Pro Mode
        if (proSettings.focusPeakingEnabled && mode == CameraMode.PRO) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 1.5f.dp.toPx()
                val neon = PixelFocusPeakingGreen.copy(alpha = 0.6f)
                drawCircle(
                    color = neon,
                    radius = 36.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = strokeW)
                )
            }
        }

        // 3. Dual-Axis 3D Horizon Leveler
        DualAxisLeveler(
            leveler = leveler,
            modifier = Modifier.align(Alignment.Center)
        )

        // 4. Floating Top Bar with Quick Settings Pill & Status Badges
        FloatingTopBar(
            mode = mode,
            flashMode = flashMode,
            captureFormat = captureFormat,
            colorProfile = colorProfile,
            ultraHdr = ultraHdr,
            timerDuration = timerDuration,
            onOpenQuickSettings = onOpenQuickSettings,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 5. Video Recording Active HUD
        VideoRecordingHUD(
            isRecording = isRecording,
            recordingDurationSeconds = recordingDurationSeconds,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 56.dp)
        )

        // 6. Countdown Timer Animated Overlay
        CountdownOverlay(
            message = captureProgress.message,
            modifier = Modifier.align(Alignment.Center)
        )

        // 7. Computational Capture Progress Banner
        ComputationalCaptureBanner(
            captureProgress = captureProgress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}
