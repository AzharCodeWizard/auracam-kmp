package com.auracam.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.auracam.camera.domain.*
import com.auracam.ui.components.*
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
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Viewfinder Viewport Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Aspect Ratio Viewfinder Frame
                val ratioModifier = when (aspectRatio) {
                    AspectRatio.RATIO_4_3 -> Modifier.aspectRatio(3f / 4f)
                    AspectRatio.RATIO_16_9 -> Modifier.aspectRatio(9f / 16f)
                    AspectRatio.RATIO_1_1 -> Modifier.aspectRatio(1f)
                    AspectRatio.RATIO_FULL -> Modifier.fillMaxSize()
                }

                BoxWithConstraints(
                    modifier = ratioModifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val normX = offset.x / size.width
                                val normY = offset.y / size.height
                                engine.setFocusPoint(normX, normY)
                                soundAndHaptics.vibrateSnap()
                            }
                        }
                ) {
                    // 1. Live Hardware Camera Viewfinder Stream
                    CameraPreview(
                        engine = engine,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Color Profile / LUT Live Tone Overlay
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

                    // 3. Focus Bracket & Dual Exposure Sliders
                    FocusBracketOverlay(
                        focusPoint = focusPoint,
                        proSettings = proSettings,
                        onProSettingsChange = { engine.updateProSettings(it) }
                    )

                    // 4. Viewfinder Overlays (Leveler, Grid, HDR Badges, Quick Settings Pill)
                    ViewfinderOverlay(
                        mode = cameraMode,
                        flashMode = flashMode,
                        captureFormat = captureFormat,
                        colorProfile = colorProfile,
                        ultraHdr = ultraHdrEnabled,
                        watermarkEnabled = watermarkEnabled,
                        timerDuration = timerDuration,
                        gridType = gridType,
                        proSettings = proSettings,
                        leveler = horizonLeveler,
                        isRecording = isRecording,
                        recordingDurationSeconds = recordingDurationSeconds,
                        captureProgress = captureProgress,
                        onOpenQuickSettings = {
                            showQuickSettings = true
                            soundAndHaptics.vibrateSnap()
                        },
                        onOpenSettings = onOpenSettings
                    )

                    // 5. Floating Zoom Selector Bar
                    ZoomSelector(
                        currentZoom = zoomRatio,
                        onZoomSelected = {
                            engine.setZoom(it)
                            soundAndHaptics.vibrateSnap()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }
            }

            // Bottom Controls Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(top = 4.dp, bottom = 8.dp)
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

                // Shutter Row (Gallery Thumbnail, Shutter Button, Camera Flip)
                ShutterRow(
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

        // Quick Settings Dropdown Sheet
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
