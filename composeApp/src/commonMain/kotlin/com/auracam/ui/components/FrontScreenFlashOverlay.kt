package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.FlashMode
import com.auracam.camera.domain.LensFacing
import com.auracam.ui.theme.*

/**
 * Pixel-Style Front Camera Screen Flash & Selfie Warm Light Ring
 * Provides screen illumination for low-light selfie photography and video.
 */
@Composable
fun FrontScreenFlashOverlay(
    currentLens: LensFacing,
    flashMode: FlashMode,
    isCapturing: Boolean,
    modifier: Modifier = Modifier
) {
    if (currentLens != LensFacing.FRONT) return

    val isIlluminating = flashMode == FlashMode.ON || flashMode == FlashMode.TORCH || (isCapturing && flashMode != FlashMode.OFF)

    AnimatedVisibility(
        visible = isIlluminating,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isCapturing && flashMode != FlashMode.OFF) {
                // Full Screen Bright Flash Burst during photo capture
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFFFDE7)) // Warm Studio Softbox White
                )
            } else {
                // Ambient Selfie Softbox Light Ring (Torch / Flash-On preview)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 24.dp,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFFAEE),
                                    Color(0xFFFFE082),
                                    Color(0xAAFFB74D)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                )

                // Top Notification Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp)
                        .pixelGlass(
                            shape = RoundedCornerShape(50),
                            backgroundColor = PixelGlassScrimHeavy,
                            borderColor = PixelYellowAccent
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (flashMode == FlashMode.TORCH) Icons.Default.Lightbulb else Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = PixelYellowAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (flashMode == FlashMode.TORCH) "SELFIE LIGHT ACTIVE" else "SCREEN FLASH READY",
                            color = PixelYellowAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
