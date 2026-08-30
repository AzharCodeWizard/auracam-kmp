package com.auracam.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import com.auracam.settings.AppSettings
import com.auracam.ui.util.SoundAndHaptics
import com.auracam.ui.util.rememberPlatformShare
import com.auracam.ui.util.rememberSoundAndHaptics
import kotlinx.coroutines.launch

@Composable
fun ViewfinderScreen(
    engine: CameraEngine,
    settings: AppSettings,
    microphoneGranted: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val platformFeedback = rememberSoundAndHaptics()
    val soundAndHaptics = remember(platformFeedback, settings.shutterSoundEnabled, settings.hapticsEnabled) {
        GatedSoundAndHaptics(
            delegate = platformFeedback,
            soundEnabled = settings.shutterSoundEnabled,
            hapticsEnabled = settings.hapticsEnabled
        )
    }

    val cameraMode by engine.cameraMode.collectAsState()
    val currentLens by engine.currentLens.collectAsState()
    val flashMode by engine.flashMode.collectAsState()
    val aspectRatio by engine.aspectRatio.collectAsState()
    val colorProfile by engine.colorProfile.collectAsState()
    val captureFormat by engine.captureFormat.collectAsState()
    val timerDuration by engine.timerDuration.collectAsState()
    val gridType by engine.gridType.collectAsState()
    val proSettings by engine.proSettings.collectAsState()
    val captureProgress by engine.captureProgress.collectAsState()
    val isRecording by engine.isRecording.collectAsState()
    val focusPoint by engine.focusPoint.collectAsState()
    val watermarkEnabled by engine.watermarkEnabled.collectAsState()
    val ultraHdrEnabled by engine.ultraHdrEnabled.collectAsState()
    val recentMedia by engine.recentMedia.collectAsState()
    val galleryList by engine.galleryList.collectAsState()

    var showQuickSettings by remember { mutableStateOf(false) }
    var showFilterDrawer by remember { mutableStateOf(false) }
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
                onOpenFilterDrawer = {
                    showFilterDrawer = !showFilterDrawer
                    soundAndHaptics.vibrateSnap()
                },
                onToggleUltraHdr = {
                    engine.toggleUltraHdr(!ultraHdrEnabled)
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
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) {
                                    val currentZ = engine.zoomRatio.value
                                    val newZoom = (currentZ * zoom).coerceIn(0.5f, 10.0f)
                                    engine.setZoom((newZoom * 10).toInt() / 10f)
                                }
                            }
                        }
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
                        ColorProfile.VINTAGE_FILM -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x188D6E63))
                            )
                        }
                        ColorProfile.COOL_BREEZE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x180099FF))
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
                        ColorProfile.CLEAN_DOC -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x10FFFFFF))
                            )
                        }
                        else -> {}
                    }

                    // Multi-Stream Dual Vlog / Director's View Overlay
                    if (cameraMode == CameraMode.DUAL_VLOG) {
                        DualVlogOverlay(
                            isRecording = isRecording,
                            onFlipStream = {
                                val nextLens = if (currentLens == LensFacing.FRONT) LensFacing.BACK_WIDE else LensFacing.FRONT
                                engine.setLens(nextLens)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ExposureMaskLayer(
                        engine = engine,
                        modifier = Modifier.fillMaxSize()
                    )

                    // C. Framing Grids
                    FramingGridOverlay(
                        gridType = if (settings.framingHintsEnabled) gridType else GridType.NONE,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (settings.framingHintsEnabled) {
                        LevelerLayer(
                            engine = engine,
                            onLevelReached = soundAndHaptics::vibrateLevelLock,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

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
                RecordingHudLayer(
                    engine = engine,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                )

                val modeNotice = cameraMode.singleFrameNotice
                if (modeNotice != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 44.dp)
                            .pixelGlass(
                                shape = RoundedCornerShape(50),
                                backgroundColor = PixelGlassScrimHeavy,
                                borderColor = PixelGlassBorder
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = modeNotice,
                            style = AuraCamTheme.cameraTypography.hudMetric,
                            color = PixelTextSecondary
                        )
                    }
                }

                if (!microphoneGranted &&
                    (cameraMode == CameraMode.VIDEO || cameraMode == CameraMode.CINEMATIC)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .pixelGlass(
                                shape = RoundedCornerShape(50),
                                backgroundColor = PixelGlassScrimHeavy,
                                borderColor = PixelGlassBorder
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Microphone off — recording without audio",
                            style = AuraCamTheme.cameraTypography.hudMetric,
                            color = PixelTextSecondary
                        )
                    }
                }
            }

            // 3. Floating Zoom Selector Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                ZoomSelectorLayer(
                    engine = engine,
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
                    ProControlsLayer(
                        engine = engine,
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
                    recordingDurationSeconds = 0,
                    captureProgress = captureProgress,
                    recentMedia = recentMedia,
                    onShutterClick = {
                        soundAndHaptics.vibrateSnap()
                        coroutineScope.launch {
                            if (cameraMode == CameraMode.VIDEO || cameraMode == CameraMode.CINEMATIC) {
                                if (!isRecording) soundAndHaptics.playVideoStartSound() else soundAndHaptics.playVideoStopSound()
                                runCatching { engine.toggleVideoRecording() }
                            } else {
                                soundAndHaptics.playShutterSound()
                                runCatching { engine.capturePhoto() }
                            }
                        }
                    },
                    onFlipCamera = {
                        soundAndHaptics.vibrateSnap()
                        val nextLens = if (currentLens == LensFacing.FRONT) LensFacing.BACK_WIDE else LensFacing.FRONT
                        engine.setLens(nextLens)
                    },
                    onGalleryClick = {
                        showGalleryPreview = true
                        soundAndHaptics.vibrateSnap()
                        coroutineScope.launch { engine.refreshGallery() }
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

        // Live Filters / LUTs Drawer Overlay
        AnimatedVisibility(
            visible = showFilterDrawer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 190.dp)
        ) {
            FilterDrawer(
                currentColorProfile = colorProfile,
                onColorProfileSelected = {
                    engine.setColorProfile(it)
                    soundAndHaptics.vibrateSnap()
                },
                onDismiss = { showFilterDrawer = false }
            )
        }

        // Fullscreen Gallery Preview Sheet
        if (showGalleryPreview) {
            val shareMedia = rememberPlatformShare()
            val initialIndex = remember(galleryList, recentMedia) {
                galleryList.indexOfFirst { it.id == recentMedia?.id }.coerceAtLeast(0)
            }
            GalleryPreviewSheet(
                media = galleryList,
                initialIndex = initialIndex,
                watermarkEnabled = watermarkEnabled,
                onShare = shareMedia,
                onDelete = { engine.deleteMedia(it) },
                onLoadExif = { engine.loadExif(it) },
                onClose = { showGalleryPreview = false }
            )
        }
    }
}

private class GatedSoundAndHaptics(
    private val delegate: SoundAndHaptics,
    private val soundEnabled: Boolean,
    private val hapticsEnabled: Boolean
) : SoundAndHaptics {
    override fun playShutterSound() {
        if (soundEnabled) delegate.playShutterSound()
    }

    override fun playVideoStartSound() {
        if (soundEnabled) delegate.playVideoStartSound()
    }

    override fun playVideoStopSound() {
        if (soundEnabled) delegate.playVideoStopSound()
    }

    override fun vibrateSnap() {
        if (hapticsEnabled) delegate.vibrateSnap()
    }

    override fun vibrateLevelLock() {
        if (hapticsEnabled) delegate.vibrateLevelLock()
    }

    override fun release() = Unit
}

@Composable
private fun LevelerLayer(
    engine: CameraEngine,
    onLevelReached: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leveler by engine.horizonLeveler.collectAsState()

    LaunchedEffect(leveler.isLevel) {
        if (leveler.isLevel) onLevelReached()
    }

    DualAxisLeveler(leveler = leveler, modifier = modifier)
}

@Composable
private fun ExposureMaskLayer(engine: CameraEngine, modifier: Modifier = Modifier) {
    val mask by engine.exposureMask.collectAsState()
    ExposureMaskOverlay(mask = mask, modifier = modifier)
}

@Composable
private fun RecordingHudLayer(engine: CameraEngine, modifier: Modifier = Modifier) {
    val isRecording by engine.isRecording.collectAsState()
    val seconds by engine.recordingDurationSeconds.collectAsState()
    VideoRecordingHUD(
        isRecording = isRecording,
        recordingDurationSeconds = seconds,
        modifier = modifier
    )
}

@Composable
private fun ZoomSelectorLayer(
    engine: CameraEngine,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoom by engine.zoomRatio.collectAsState()
    val presets by engine.availableZoomPresets.collectAsState()
    ZoomSelector(
        currentZoom = zoom,
        availablePresets = presets,
        onZoomSelected = onZoomSelected,
        modifier = modifier
    )
}

@Composable
private fun ProControlsLayer(engine: CameraEngine, modifier: Modifier = Modifier) {
    val proSettings by engine.proSettings.collectAsState()
    val histogram by engine.liveHistogram.collectAsState()
    ProControlsSheet(
        proSettings = proSettings,
        histogramData = histogram,
        onProSettingsChange = { engine.updateProSettings(it) },
        modifier = modifier
    )
}
