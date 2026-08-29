package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.*
import com.auracam.ui.theme.PixelFocusPeakingGreen
import com.auracam.ui.theme.PixelLevelerGreen
import com.auracam.ui.theme.PixelRecordRed
import com.auracam.ui.theme.PixelYellowAccent

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
    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Grid Overlays
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c = Color(0x33FFFFFF)
            val strokeW = 1.dp.toPx()

            when (gridType) {
                GridType.RULE_OF_THIRDS -> {
                    // 3x3 Grid
                    drawLine(c, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeW)
                    drawLine(c, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), strokeW)
                    drawLine(c, Offset(0f, h / 3f), Offset(w, h / 3f), strokeW)
                    drawLine(c, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), strokeW)
                }
                GridType.GOLDEN_RATIO -> {
                    // Golden Ratio (0.382 and 0.618)
                    drawLine(c, Offset(w * 0.382f, 0f), Offset(w * 0.382f, h), strokeW)
                    drawLine(c, Offset(w * 0.618f, 0f), Offset(w * 0.618f, h), strokeW)
                    drawLine(c, Offset(0f, h * 0.382f), Offset(w, h * 0.382f), strokeW)
                    drawLine(c, Offset(0f, h * 0.618f), Offset(w, h * 0.618f), strokeW)
                }
                GridType.SQUARE -> {
                    // 1:1 Center Square framing box
                    val squareSize = w.coerceAtMost(h) * 0.85f
                    val left = (w - squareSize) / 2f
                    val top = (h - squareSize) / 2f
                    drawRect(
                        color = Color(0x55FFFFFF),
                        topLeft = Offset(left, top),
                        size = Size(squareSize, squareSize),
                        style = Stroke(width = strokeW)
                    )
                }
                GridType.NONE -> { }
            }
        }

        // 2. Focus Peaking Neon Green Overlay in Pro Mode
        if (proSettings.focusPeakingEnabled && mode == CameraMode.PRO) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 1.5f.dp.toPx()
                val neon = PixelFocusPeakingGreen.copy(alpha = 0.5f)
                // Outline high contrast focus region indicators
                drawCircle(
                    color = neon,
                    radius = 35.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = strokeW)
                )
            }
        }

        // 3. 3D Horizon Leveler (Pixel Leveler with degree snap)
        Box(
            modifier = Modifier.align(Alignment.Center).size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val levelColor = if (leveler.isLevel) PixelLevelerGreen else Color(0x99FFFFFF)
                val strokeW = if (leveler.isLevel) 2.5f.dp.toPx() else 1.5f.dp.toPx()

                // Horizon Bar
                drawLine(
                    color = levelColor,
                    start = Offset(20f, size.height / 2f + leveler.rollDegrees * 3f),
                    end = Offset(size.width - 20f, size.height / 2f - leveler.rollDegrees * 3f),
                    strokeWidth = strokeW
                )

                // Center crosshair notch
                drawCircle(
                    color = levelColor,
                    radius = 3.dp.toPx(),
                    center = centerOffset
                )
            }

            if (leveler.isLevel) {
                Box(
                    modifier = Modifier
                        .offset(y = (-24).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PixelLevelerGreen)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "0° LEVEL",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Top Status & Quick Settings Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Settings Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x881E1E1E))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOpenQuickSettings() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
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
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Status Badges (Color Profile, Ultra HDR, RAW, Flash, Timer)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Color Profile Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x55333333))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(colorProfile.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                if (ultraHdr) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x66FFDB58))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("ULTRA HDR", color = PixelYellowAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (captureFormat == CaptureFormat.RAW_DNG || captureFormat == CaptureFormat.RAW_PLUS_JPEG) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x668AB4F8))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("RAW", color = Color(0xFF8AB4F8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (flashMode != FlashMode.OFF) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (flashMode == FlashMode.TORCH) PixelYellowAccent else Color(0x66FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            flashMode.title.uppercase(),
                            color = if (flashMode == FlashMode.TORCH) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (timerDuration != TimerDuration.OFF) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x66FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(timerDuration.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x881E1E1E))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "App Settings",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 5. Video Recording HUD
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xDD000000))
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
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "4K 60FPS",
                        color = PixelYellowAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 6. Countdown Timer Animated Center Overlay
        if (captureProgress.message.startsWith("Timer:")) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = captureProgress.message.substringAfter("Timer: "),
                    color = PixelYellowAccent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 7. Computational Capture Progress Banner
        AnimatedVisibility(
            visible = captureProgress.state != CaptureState.IDLE && captureProgress.state != CaptureState.COMPLETE && !captureProgress.message.startsWith("Timer:"),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE1E1E1E))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = captureProgress.message,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LinearProgressIndicator(
                        progress = { captureProgress.progress },
                        modifier = Modifier.width(180.dp).height(4.dp),
                        color = PixelYellowAccent,
                        trackColor = Color(0xFF444444)
                    )
                }
            }
        }
    }
}
