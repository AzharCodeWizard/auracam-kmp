package com.auracam.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.auracam.camera.domain.*
import com.auracam.ui.components.*
import com.auracam.ui.theme.*
import com.auracam.ui.util.PlatformSoundAndHaptics
import com.auracam.ui.util.rememberPlatformShare
import kotlinx.coroutines.launch

@Composable
fun ViewfinderScreen(
    engine: CameraEngine,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val soundAndHaptics = remember { PlatformSoundAndHaptics() }

    val cameraMode by engine.cameraMode.collectAsState()
    val currentLens by engine.currentLens.collectAsState()
    val zoomRatio by engine.zoomRatio.collectAsState()
    val flashMode by engine.flashMode.collectAsState()
    val aspectRatio by engine.aspectRatio.collectAsState()
    val colorProfile by engine.colorProfile.collectAsState()
    val captureFormat by engine.captureFormat.collectAsState()
    val timerDuration by engine.timerDuration.collectAsState()
    val gridType by engine.gridType.collectAsState()
    val proSettings by engine.proSettings.collectAsState()
    val liveHistogram by engine.liveHistogram.collectAsState()
    val horizonLeveler by engine.horizonLeveler.collectAsState()
    val captureProgress by engine.captureProgress.collectAsState()
    val isRecording by engine.isRecording.collectAsState()
    val recordingDurationSeconds by engine.recordingDurationSeconds.collectAsState()
    val focusPoint by engine.focusPoint.collectAsState()
    val watermarkEnabled by engine.watermarkEnabled.collectAsState()
    val ultraHdrEnabled by engine.ultraHdrEnabled.collectAsState()
    val recentMedia by engine.recentMedia.collectAsState()

    var showQuickSettings by remember { mutableStateOf(false) }
    var showGalleryPreview by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPitchBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Pinned Top Safe Header with Floating Top Bar
            FloatingTopBar(
                mode = cameraMode,
                flashMode = flashMode,
                captureFormat = captureFormat,
                colorProfile = colorProfile,
                ultraHdr = ultraHdrEnabled,
                timerDuration = timerDuration,
                onOpenQuickSettings = {
                    showQuickSettings = true
                    soundAndHaptics.vibrateSnap()
                },
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
            )

            // 2. Centered Viewfinder Viewport Frame
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val ratioModifier = when (aspectRatio) {
                    AspectRatio.RATIO_4_3 -> Modifier.aspectRatio(3f / 4f)
                    AspectRatio.RATIO_16_9 -> Modifier.aspectRatio(9f / 16f)
                    AspectRatio.RATIO_1_1 -> Modifier.aspectRatio(1f)
                    AspectRatio.RATIO_FULL -> Modifier.fillMaxSize()
                }

                BoxWithConstraints(
                    modifier = ratioModifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(PixelDarkBackground)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val normX = offset.x / size.width
                                val normY = offset.y / size.height
                                engine.setFocusPoint(normX, normY)
                                soundAndHaptics.vibrateSnap()
                            }
                        }
                ) {
                    // A. Live Hardware Camera Viewfinder Stream
                    CameraPreview(
                        engine = engine,
                        modifier = Modifier.fillMaxSize()
                    )

                    // B. Color Profile / LUT Live Tone Overlay
                    when (colorProfile) {
                        ColorProfile.HIGH_CONTRAST_MONO -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x33000000))
                            )
                        }
                        ColorProfile.CINEMATIC_WARM -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x15FFAA00))
                            )
                        }
                        ColorProfile.ASTRO_BOOST -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x1500AAFF))
                            )
                        }
                        ColorProfile.VIBRANT -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x10FF5500))
                            )
                        }
                        else -> {}
                    }

                    // C. Framing Grids
                    FramingGridOverlay(
                        gridType = gridType,
                        modifier = Modifier.fillMaxSize()
                    )

                    // D. 3D Dual-Axis Leveler
                    DualAxisLeveler(
                        leveler = horizonLeveler,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // E. Focus Bracket & Exposure Sliders
                    FocusBracketOverlay(
                        focusPoint = focusPoint,
                        proSettings = proSettings,
                        onProSettingsChange = { engine.updateProSettings(it) }
                    )

                    // F. Center Countdown Overlay
                    CountdownOverlay(
                        message = captureProgress.message,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // G. Computational Progress Banner
                    ComputationalCaptureBanner(
                        captureProgress = captureProgress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp)
                    )
                }

                // Video Recording Active HUD (Floating in Viewport)
                VideoRecordingHUD(
                    isRecording = isRecording,
                    recordingDurationSeconds = recordingDurationSeconds,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                )
            }

            // 3. Floating Zoom Selector Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                ZoomSelector(
                    currentZoom = zoomRatio,
                    onZoomSelected = {
                        engine.setZoom(it)
                        soundAndHaptics.vibrateSnap()
                    }
                )
            }

            // 4. Pinned Bottom Controls Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PixelPitchBlack)
                    .padding(bottom = 8.dp)
            ) {
                // Pro Controls Sheet (when in Pro mode)
                AnimatedVisibility(
                    visible = cameraMode == CameraMode.PRO,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ProControlsSheet(
                        proSettings = proSettings,
                        histogramData = liveHistogram,
                        onProSettingsChange = { engine.updateProSettings(it) },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Mode Carousel
                ModeCarousel(
                    currentMode = cameraMode,
                    onModeSelected = {
                        engine.setMode(it)
                        soundAndHaptics.vibrateSnap()
                    }
                )

                // Shutter Control Row (Gallery Thumbnail, Shutter Button, Camera Flip)
                ShutterControlRow(
                    mode = cameraMode,
                    isRecording = isRecording,
                    recordingDurationSeconds = recordingDurationSeconds,
                    captureProgress = captureProgress,
                    recentMedia = recentMedia,
                    onShutterClick = {
                        soundAndHaptics.vibrateSnap()
                        coroutineScope.launch {
                            if (cameraMode == CameraMode.VIDEO || cameraMode == CameraMode.CINEMATIC) {
                                if (!isRecording) soundAndHaptics.playVideoStartSound() else soundAndHaptics.playVideoStopSound()
                                engine.toggleVideoRecording()
                            } else {
                                soundAndHaptics.playShutterSound()
                                engine.capturePhoto()
                            }
                        }
                    },
                    onFlipCamera = {
                        soundAndHaptics.vibrateSnap()
                        val nextLens = if (currentLens == LensFacing.FRONT) LensFacing.BACK_WIDE else LensFacing.FRONT
                        engine.setLens(nextLens)
                    },
                    onGalleryClick = {
                        if (recentMedia != null) {
                            showGalleryPreview = true
                            soundAndHaptics.vibrateSnap()
                        }
                    }
                )
            }
        }

        // Quick Settings Dropdown Sheet (Pixel Glass Card)
        AnimatedVisibility(
            visible = showQuickSettings,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            QuickSettingsDialog(
                aspectRatio = aspectRatio,
                timerDuration = timerDuration,
                flashMode = flashMode,
                captureFormat = captureFormat,
                colorProfile = colorProfile,
                gridType = gridType,
                ultraHdr = ultraHdrEnabled,
                watermarkEnabled = watermarkEnabled,
                onAspectRatioChange = {
                    engine.setAspectRatio(it)
                    soundAndHaptics.vibrateSnap()
                },
                onTimerChange = {
                    engine.setTimer(it)
                    soundAndHaptics.vibrateSnap()
                },
                onFlashChange = {
                    engine.setFlash(it)
                    soundAndHaptics.vibrateSnap()
                },
                onCaptureFormatChange = {
                    engine.setCaptureFormat(it)
                    soundAndHaptics.vibrateSnap()
                },
                onColorProfileChange = {
                    engine.setColorProfile(it)
                    soundAndHaptics.vibrateSnap()
                },
                onGridChange = {
                    engine.setGrid(it)
                    soundAndHaptics.vibrateSnap()
                },
                onUltraHdrToggle = {
                    engine.toggleUltraHdr(it)
                    soundAndHaptics.vibrateSnap()
                },
                onWatermarkToggle = {
                    engine.toggleWatermark(it)
                    soundAndHaptics.vibrateSnap()
                },
                onDismiss = { showQuickSettings = false }
            )
        }

        // Fullscreen Gallery Preview Sheet
        if (showGalleryPreview && recentMedia != null) {
            val shareMedia = rememberPlatformShare()
            GalleryPreviewSheet(
                media = recentMedia!!,
                watermarkEnabled = watermarkEnabled,
                onShare = shareMedia,
                onClose = { showGalleryPreview = false }
            )
        }
    }
}
