package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.*
import com.auracam.ui.theme.*
import kotlin.math.abs

// =========================================================================
// Authentic Google Pixel Camera Viewfinder Controls & HUD Overlays
// =========================================================================

/**
 * 1. Floating Top Action Bar: Glass pill container for Quick Settings, Badges, and Filter Drawer
 */
@Composable
fun FloatingTopBar(
    mode: CameraMode,
    currentLens: LensFacing = LensFacing.BACK_WIDE,
    flashMode: FlashMode,
    captureFormat: CaptureFormat,
    colorProfile: ColorProfile,
    photoResolution: PhotoResolution = PhotoResolution.STANDARD_12MP,
    videoResolution: VideoResolution = VideoResolution.FHD_1080P_30,
    ultraHdr: Boolean,
    timerDuration: TimerDuration,
    onOpenQuickSettings: () -> Unit,
    onOpenFilterDrawer: () -> Unit = {},
    onToggleUltraHdr: () -> Unit = {},
    onToggleFlash: () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideoMode = mode == CameraMode.VIDEO || mode == CameraMode.CINEMATIC || mode == CameraMode.DUAL_VLOG || mode == CameraMode.SLOW_MOTION || mode == CameraMode.TIME_LAPSE
    val resolutionBadge = if (isVideoMode) videoResolution.shortBadge else photoResolution.shortBadge

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick Settings Pill
        Row(
            modifier = Modifier
                .pixelGlass(
                    shape = CircleShape,
                    backgroundColor = PixelGlassScrim,
                    borderColor = PixelGlassBorder
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenQuickSettings() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Quick Settings",
                tint = PixelYellowAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Settings",
                style = AuraCamTheme.cameraTypography.pillLabel,
                color = PixelTextWhite
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = PixelTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        // Status Badges, Flash Toggle, Filter Wand & Settings Gear
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Interactive Flash Badge / Quick Toggle (Supported on both Front and Back cameras)
            val flashLabel = when (flashMode) {
                FlashMode.OFF -> if (currentLens == LensFacing.FRONT) "⚡ OFF" else "⚡ OFF"
                FlashMode.AUTO -> "⚡ AUTO"
                FlashMode.ON -> if (currentLens == LensFacing.FRONT) "⚡ FLASH" else "⚡ ON"
                FlashMode.TORCH -> if (currentLens == LensFacing.FRONT) "💡 LIGHT" else "💡 TORCH"
            }

            PixelGlassBadge(
                text = flashLabel,
                textColor = if (flashMode != FlashMode.OFF) PixelYellowAccent else PixelTextSecondary,
                containerColor = if (flashMode != FlashMode.OFF) Color(0x44FFDB58) else PixelGlassPill,
                borderColor = if (flashMode != FlashMode.OFF) PixelYellowAccent.copy(alpha = 0.5f) else PixelGlassBorderSubtle,
                onClick = onToggleFlash
            )

            // Resolution Badge (Clickable to open resolution switcher)
            PixelGlassBadge(
                text = resolutionBadge,
                textColor = PixelTextWhite,
                containerColor = PixelGlassPill,
                borderColor = PixelGlassBorderSubtle,
                onClick = onOpenQuickSettings
            )

            // Interactive Color Profile / Filter Badge
            PixelGlassBadge(
                text = colorProfile.label.uppercase(),
                textColor = if (colorProfile != ColorProfile.NATURAL) PixelYellowAccent else PixelTextPrimary,
                containerColor = if (colorProfile != ColorProfile.NATURAL) Color(0x55FFDB58) else PixelGlassPill,
                borderColor = if (colorProfile != ColorProfile.NATURAL) PixelYellowAccent.copy(alpha = 0.5f) else PixelGlassBorderSubtle,
                onClick = onOpenFilterDrawer
            )

            // Interactive Ultra HDR Badge (Click to Toggle On/Off)
            if (ultraHdr) {
                PixelGlassBadge(
                    text = "ULTRA HDR",
                    textColor = PixelYellowAccent,
                    containerColor = PixelYellowContainer,
                    borderColor = PixelYellowAccent.copy(alpha = 0.3f),
                    onClick = onToggleUltraHdr
                )
            }

            // RAW Format Badge
            if (captureFormat == CaptureFormat.RAW_DNG || captureFormat == CaptureFormat.RAW_PLUS_JPEG) {
                PixelGlassBadge(
                    text = "RAW",
                    textColor = PixelGoogleBlue,
                    containerColor = PixelBlueContainer,
                    borderColor = PixelGoogleBlue.copy(alpha = 0.3f)
                )
            }

            // Flash Status Badge
            if (flashMode != FlashMode.OFF) {
                PixelGlassBadge(
                    text = flashMode.title.uppercase(),
                    textColor = if (flashMode == FlashMode.TORCH) PixelTextOnYellow else PixelTextWhite,
                    containerColor = if (flashMode == FlashMode.TORCH) PixelYellowAccent else PixelGlassPill,
                    borderColor = PixelGlassBorderSubtle
                )
            }

            // Filter Wand Button
            IconButton(
                onClick = onOpenFilterDrawer,
                modifier = Modifier
                    .size(38.dp)
                    .pixelGlass(
                        shape = CircleShape,
                        backgroundColor = if (colorProfile != ColorProfile.NATURAL) Color(0x66FFDB58) else PixelGlassScrim,
                        borderColor = if (colorProfile != ColorProfile.NATURAL) PixelYellowAccent else PixelGlassBorder
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Camera Filters",
                    tint = if (colorProfile != ColorProfile.NATURAL) PixelYellowAccent else PixelTextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }

            // App Settings Gear Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .pixelGlass(
                        shape = CircleShape,
                        backgroundColor = PixelGlassScrim,
                        borderColor = PixelGlassBorder
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Camera Settings",
                    tint = PixelTextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 2. Dual-Axis 3D Leveler Indicator Overlay
 * Features pitch & roll visual guides, emerald green snap feedback, and high-contrast degree readout.
 */
@Composable
fun DualAxisLeveler(
    leveler: HorizonLeveler,
    modifier: Modifier = Modifier
) {
    val isLevel = leveler.isLevel
    val rollDegrees = leveler.rollDegrees
    val pitchDegrees = leveler.pitchDegrees

    val lineColor by animateColorAsState(
        targetValue = if (isLevel) PixelLevelerGreen else Color(0xCCFFFFFF),
        animationSpec = tween(durationMillis = 180)
    )

    val strokeWidth by animateFloatAsState(
        targetValue = if (isLevel) 2.5f else 1.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    )

    Box(
        modifier = modifier
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokePx = strokeWidth.dp.toPx()
            val shadowStrokePx = (strokeWidth + 1.5f).dp.toPx()

            val rollOffset = (rollDegrees * 2.8f).coerceIn(-size.height / 3f, size.height / 3f)
            val pitchOffset = (pitchDegrees * 2.2f).coerceIn(-size.height / 4f, size.height / 4f)

            val startPoint = Offset(24f, center.y + rollOffset)
            val endPoint = Offset(size.width - 24f, center.y - rollOffset)

            // --- 1. Horizon Roll Line ---
            // Shadow underlay for high contrast over bright backgrounds
            drawLine(
                color = PixelHudShadow,
                start = startPoint.copy(y = startPoint.y + 1f),
                end = endPoint.copy(y = endPoint.y + 1f),
                strokeWidth = shadowStrokePx
            )
            // Foreground horizon line
            drawLine(
                color = lineColor,
                start = startPoint,
                end = endPoint,
                strokeWidth = strokePx
            )

            // --- 2. Dual-Axis Pitch Reticle (Center Guide) ---
            val pitchCenter = Offset(center.x, center.y + pitchOffset)
            val crosshairSize = 8.dp.toPx()

            // Pitch shadow crosshair
            drawLine(
                color = PixelHudShadow,
                start = Offset(pitchCenter.x - crosshairSize, pitchCenter.y + 1f),
                end = Offset(pitchCenter.x + crosshairSize, pitchCenter.y + 1f),
                strokeWidth = shadowStrokePx
            )
            drawLine(
                color = PixelHudShadow,
                start = Offset(pitchCenter.x, pitchCenter.y - crosshairSize + 1f),
                end = Offset(pitchCenter.x, pitchCenter.y + crosshairSize + 1f),
                strokeWidth = shadowStrokePx
            )

            // Pitch foreground crosshair
            drawLine(
                color = lineColor,
                start = Offset(pitchCenter.x - crosshairSize, pitchCenter.y),
                end = Offset(pitchCenter.x + crosshairSize, pitchCenter.y),
                strokeWidth = strokePx
            )
            drawLine(
                color = lineColor,
                start = Offset(pitchCenter.x, pitchCenter.y - crosshairSize),
                end = Offset(pitchCenter.x, pitchCenter.y + crosshairSize),
                strokeWidth = strokePx
            )

            // Center precision reticle ring
            drawCircle(
                color = lineColor,
                radius = 3.5.dp.toPx(),
                center = center,
                style = Stroke(width = strokePx)
            )
        }

        // Degree Pill Indicator
        if (isLevel) {
            Box(
                modifier = Modifier
                    .offset(y = (-28).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PixelLevelerGreen)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "0° LEVEL",
                    style = AuraCamTheme.cameraTypography.levelDegree,
                    color = PixelPitchBlack
                )
            }
        } else if (abs(rollDegrees) in 1.0f..15.0f) {
            Box(
                modifier = Modifier
                    .offset(y = (-28).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                val formattedRoll = if (rollDegrees > 0) "+${rollDegrees.toInt()}°" else "${rollDegrees.toInt()}°"
                Text(
                    text = formattedRoll,
                    style = AuraCamTheme.cameraTypography.hudMetric,
                    color = PixelTextPrimary
                )
            }
        }
    }
}

/**
 * 3. High-Contrast Framing Grid Overlay (Rule of Thirds, Golden Ratio, Square)
 */
@Composable
fun FramingGridOverlay(
    gridType: GridType,
    modifier: Modifier = Modifier
) {
    if (gridType == GridType.NONE) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val hairlineColor = Color(0x38FFFFFF)
        val shadowColor = PixelGridShadow
        val strokeW = 1.dp.toPx()
        val shadowStrokeW = 2.dp.toPx()

        fun drawGridLine(start: Offset, end: Offset) {
            // Shadow stroke for 100% legibility on white/bright scenes
            drawLine(shadowColor, Offset(start.x + 0.5f, start.y + 0.5f), Offset(end.x + 0.5f, end.y + 0.5f), shadowStrokeW)
            // Primary hairline
            drawLine(hairlineColor, start, end, strokeW)
        }

        when (gridType) {
            GridType.RULE_OF_THIRDS -> {
                // Vertical lines
                drawGridLine(Offset(w / 3f, 0f), Offset(w / 3f, h))
                drawGridLine(Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h))
                // Horizontal lines
                drawGridLine(Offset(0f, h / 3f), Offset(w, h / 3f))
                drawGridLine(Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f))

                // Center crosshair notch
                val cx = w / 2f
                val cy = h / 2f
                val notchSize = 6.dp.toPx()
                drawGridLine(Offset(cx - notchSize, cy), Offset(cx + notchSize, cy))
                drawGridLine(Offset(cx, cy - notchSize), Offset(cx, cy + notchSize))
            }
            GridType.GOLDEN_RATIO -> {
                // Golden ratio lines (0.382 and 0.618)
                drawGridLine(Offset(w * 0.382f, 0f), Offset(w * 0.382f, h))
                drawGridLine(Offset(w * 0.618f, 0f), Offset(w * 0.618f, h))
                drawGridLine(Offset(0f, h * 0.382f), Offset(w, h * 0.382f))
                drawGridLine(Offset(0f, h * 0.618f), Offset(w, h * 0.618f))
            }
            GridType.SQUARE -> {
                // 1:1 Center Square framing box
                val squareSize = w.coerceAtMost(h) * 0.85f
                val left = (w - squareSize) / 2f
                val top = (h - squareSize) / 2f

                // Shadow rect
                drawRect(
                    color = shadowColor,
                    topLeft = Offset(left + 0.5f, top + 0.5f),
                    size = Size(squareSize, squareSize),
                    style = Stroke(width = shadowStrokeW)
                )
                // Foreground rect
                drawRect(
                    color = Color(0x66FFFFFF),
                    topLeft = Offset(left, top),
                    size = Size(squareSize, squareSize),
                    style = Stroke(width = strokeW)
                )
            }
            GridType.NONE -> { }
        }
    }
}

/**
 * 4. Expressive Layered Shutter Button with Tactile Spring Depression Effect
 */
@Composable
fun ExpressiveShutterButton(
    mode: CameraMode,
    isRecording: Boolean,
    isCapturing: Boolean,
    captureProgress: CaptureProgress,
    onShutterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile spring depression scale
    val buttonScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.88f
            isCapturing -> 0.94f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    // Inner core color transition
    val isVideoMode = mode == CameraMode.VIDEO || mode == CameraMode.CINEMATIC || mode == CameraMode.DUAL_VLOG || mode == CameraMode.SLOW_MOTION || mode == CameraMode.TIME_LAPSE
    val coreColor by animateColorAsState(
        targetValue = when {
            isRecording || isVideoMode -> PixelRecordRed
            else -> PixelTextWhite
        },
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = modifier
            .size(88.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .clickable(
                enabled = !isCapturing,
                interactionSource = interactionSource,
                indication = null
            ) { onShutterClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer concentric white ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(3.5.dp, PixelTextWhite, CircleShape)
        )

        // Inner tactile core disc
        if (isRecording) {
            // Recording state: Red rounded square
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PixelRecordRed)
            )
        } else {
            // Idle / Photo / Video circle core
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(coreColor)
            )
        }

        // Circular progress spinner ring during computational processing
        if (isCapturing) {
            CircularProgressIndicator(
                progress = { captureProgress.progress },
                modifier = Modifier.size(82.dp),
                color = PixelYellowAccent,
                strokeWidth = 4.dp
            )
        }
    }
}

/**
 * 5. Full Shutter Row (Gallery Thumbnail, Expressive Shutter Button, Switch Camera)
 */
@Composable
fun ShutterControlRow(
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
    val isCapturing = captureProgress.state != CaptureState.IDLE && captureProgress.state != CaptureState.COMPLETE
    var flipRotation by remember { mutableStateOf(0f) }

    val animatedFlipRotation by animateFloatAsState(
        targetValue = flipRotation,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Gallery Preview Thumbnail Button
        Box(
            modifier = Modifier
                .size(56.dp)
                .pixelGlass(
                    shape = CircleShape,
                    backgroundColor = PixelSurfaceContainerHigh,
                    borderColor = PixelGlassBorder
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onGalleryClick() },
            contentAlignment = Alignment.Center
        ) {
            val thumbnail = recentMedia?.let { com.auracam.ui.util.rememberMediaImage(it.uri, maxDimension = 256) }
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail,
                    contentDescription = "Open gallery",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else if (recentMedia != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PixelSurfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = recentMedia.mode.badgeText.take(2),
                        style = AuraCamTheme.cameraTypography.badgeSmall,
                        color = PixelYellowAccent
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = PixelTextWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Center: Expressive Shutter Button
        ExpressiveShutterButton(
            mode = mode,
            isRecording = isRecording,
            isCapturing = isCapturing,
            captureProgress = captureProgress,
            onShutterClick = onShutterClick
        )

        // Right: Camera Flip Button with Spring Rotation
        Box(
            modifier = Modifier
                .size(56.dp)
                .rotate(animatedFlipRotation)
                .pixelGlass(
                    shape = CircleShape,
                    backgroundColor = PixelSurfaceContainerHigh,
                    borderColor = PixelGlassBorder
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    flipRotation += 180f
                    onFlipCamera()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = PixelTextWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 6. Video Recording Top HUD Badge
 */
@Composable
fun VideoRecordingHUD(
    isRecording: Boolean,
    recordingDurationSeconds: Int,
    modifier: Modifier = Modifier
) {
    if (!isRecording) return

    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xDD000000))
            .border(1.dp, PixelGlassBorder, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(PixelRecordRed.copy(alpha = blinkAlpha))
            )
            val mins = recordingDurationSeconds / 60
            val secs = recordingDurationSeconds % 60
            Text(
                text = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}",
                style = AuraCamTheme.cameraTypography.recordTimer,
                color = PixelTextWhite
            )
            Text(
                text = "4K 60FPS",
                style = AuraCamTheme.cameraTypography.badgeSmall,
                color = PixelYellowAccent
            )
        }
    }
}

/**
 * 7. Countdown Timer Animated Center Overlay
 */
@Composable
fun CountdownOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    if (!message.startsWith("Timer:")) return

    val count = message.substringAfter("Timer: ").trim()

    Box(
        modifier = modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(Color(0xCC000000))
            .border(1.5.dp, PixelYellowAccent.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count,
            style = AuraCamTheme.cameraTypography.countdown
        )
    }
}

/**
 * 8. Computational Capture Progress Banner
 */
@Composable
fun ComputationalCaptureBanner(
    captureProgress: CaptureProgress,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = captureProgress.state != CaptureState.IDLE &&
                captureProgress.state != CaptureState.COMPLETE &&
                !captureProgress.message.startsWith("Timer:"),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .pixelGlass(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = PixelGlassScrimHeavy,
                    borderColor = PixelGlassBorder
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = captureProgress.message,
                style = AuraCamTheme.cameraTypography.pillLabel,
                color = PixelTextWhite
            )
            LinearProgressIndicator(
                progress = { captureProgress.progress },
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                color = PixelYellowAccent,
                trackColor = Color(0xFF444444)
            )
        }
    }
}
