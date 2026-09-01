package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.FocusPoint
import com.auracam.camera.domain.ProSettings
import com.auracam.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Minimalist Pro Focus Reticle with Sleek Glass EV Brightness Slider
 */
@Composable
fun FocusBracketOverlay(
    focusPoint: FocusPoint?,
    proSettings: ProSettings,
    onProSettingsChange: ((ProSettings) -> ProSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    var isSliderVisible by remember { mutableStateOf(true) }

    LaunchedEffect(focusPoint, proSettings.evBias) {
        isSliderVisible = true
        delay(3500)
        isSliderVisible = false
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val focusPxX = maxWidth * focusPoint.x
        val focusPxY = maxHeight * focusPoint.y

        // 1. Sleek Minimalist Focus Reticle
        Box(
            modifier = Modifier
                .offset(
                    x = (focusPxX - 32.dp).coerceIn(8.dp, maxWidth - 72.dp),
                    y = (focusPxY - 32.dp).coerceIn(8.dp, maxHeight - 72.dp)
                )
                .size(64.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 1.5.dp.toPx()
                val tickLen = 10.dp.toPx()
                val reticleColor = PixelYellowAccent.copy(alpha = pulseAlpha)

                // Outer Focus Ring
                drawCircle(
                    color = reticleColor,
                    radius = size.width / 2f - 2f,
                    style = Stroke(width = strokeW)
                )

                // Center precision dot
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = reticleColor,
                    radius = 2.5.dp.toPx()
                )

                // 4 Cross ticks
                drawLine(reticleColor, Offset(center.x - tickLen, center.y), Offset(center.x - 4.dp.toPx(), center.y), strokeW)
                drawLine(reticleColor, Offset(center.x + 4.dp.toPx(), center.y), Offset(center.x + tickLen, center.y), strokeW)
                drawLine(reticleColor, Offset(center.x, center.y - tickLen), Offset(center.x, center.y - 4.dp.toPx()), strokeW)
                drawLine(reticleColor, Offset(center.x, center.y + 4.dp.toPx()), Offset(center.x, center.y + tickLen), strokeW)
            }
        }

        // 2. Ultra-Sleek Glass EV Slider
        AnimatedVisibility(
            visible = isSliderVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .offset(
                    x = (focusPxX + 40.dp).coerceIn(8.dp, maxWidth - 56.dp),
                    y = (focusPxY - 60.dp).coerceIn(8.dp, maxHeight - 140.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC101216))
                    .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaEv = -dragAmount / 35f
                            onProSettingsChange { current ->
                                val newEv = (current.evBias + deltaEv).coerceIn(-3.0f, 3.0f)
                                current.copy(evBias = kotlin.math.round(newEv * 10) / 10f)
                            }
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "EV Bias",
                    tint = PixelYellowAccent,
                    modifier = Modifier.size(15.dp)
                )

                // Slider Track
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0x44FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbOffset = (-proSettings.evBias * 9).dp.coerceIn(-28.dp, 28.dp)
                    Box(
                        modifier = Modifier
                            .offset(y = thumbOffset)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(PixelYellowAccent)
                            .border(1.dp, PixelPitchBlack, CircleShape)
                    )
                }

                // Numeric EV Readout
                Text(
                    text = "${if (proSettings.evBias >= 0) "+" else ""}${proSettings.evBias}",
                    color = PixelYellowAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
