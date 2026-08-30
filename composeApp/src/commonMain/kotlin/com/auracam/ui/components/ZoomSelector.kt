package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iPhone-style Continuous Wheel Zoom Dial + Compact Preset Pills
 */
@Composable
fun ZoomSelector(
    currentZoom: Float,
    availablePresets: List<Float> = listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f),
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpandedDial by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }

    // Auto collapse after 2.5s of no interaction
    LaunchedEffect(lastInteractionTime) {
        if (isExpandedDial && lastInteractionTime > 0) {
            delay(2500)
            isExpandedDial = false
        }
    }

    val minZoom = availablePresets.firstOrNull() ?: 1.0f
    val maxZoom = availablePresets.lastOrNull()?.coerceAtLeast(10.0f) ?: 10.0f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Expanded iPhone-style Rotary Ticker Dial
        AnimatedVisibility(
            visible = isExpandedDial,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xEE14171C))
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                // Large Active Zoom Readout
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PixelYellowAccent)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    val formattedZoom = if (currentZoom < 10f) {
                        "${(currentZoom * 10).roundToInt() / 10.0}x"
                    } else {
                        "${currentZoom.roundToInt()}x"
                    }
                    Text(
                        text = formattedZoom,
                        color = PixelPitchBlack,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Continuous Horizontal Ticker Wheel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                lastInteractionTime = System.currentTimeMillis()
                                val zoomSensitivity = 0.04f * (currentZoom.coerceAtLeast(1f) / 2f)
                                val delta = -dragAmount * zoomSensitivity
                                val newZoom = (currentZoom + delta).coerceIn(minZoom, maxZoom)
                                onZoomSelected((newZoom * 10).roundToInt() / 10f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midX = w / 2f
                        val totalTicks = 40
                        val tickSpacing = w / totalTicks

                        // Center yellow indicator needle
                        drawLine(
                            color = PixelYellowAccent,
                            start = Offset(midX, 2f),
                            end = Offset(midX, h - 2f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Relative offset based on zoom value
                        val baseOffset = (currentZoom - minZoom) / (maxZoom - minZoom) * (w * 1.5f)

                        for (i in -25..25) {
                            val tickX = midX + i * tickSpacing - (baseOffset % tickSpacing)
                            if (tickX in 0f..w) {
                                val distFromCenter = abs(tickX - midX) / (w / 2f)
                                val alpha = (1f - distFromCenter * 0.8f).coerceIn(0.1f, 0.9f)
                                val isMajor = i % 5 == 0
                                val tickHeight = if (isMajor) h * 0.6f else h * 0.35f
                                val topY = (h - tickHeight) / 2f

                                drawLine(
                                    color = if (isMajor) Color.White.copy(alpha = alpha) else Color(0xFF888888).copy(alpha = alpha),
                                    start = Offset(tickX, topY),
                                    end = Offset(tickX, topY + tickHeight),
                                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Compact Zoom Preset Pills Capsule
        Box(
            modifier = Modifier
                .pixelGlass(
                    shape = CircleShape,
                    backgroundColor = PixelGlassScrim,
                    borderColor = PixelGlassBorder
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isExpandedDial = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            lastInteractionTime = System.currentTimeMillis()
                            val delta = -dragAmount.x * 0.05f
                            val newZoom = (currentZoom + delta).coerceIn(minZoom, maxZoom)
                            onZoomSelected((newZoom * 10).roundToInt() / 10f)
                        }
                    )
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availablePresets.forEach { preset ->
                    val isSelected = abs(currentZoom - preset) < 0.25f

                    val scale = animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 0.95f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )

                    val btnBgColor = animateColorAsState(
                        targetValue = if (isSelected) PixelYellowAccent else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    val textColor = animateColorAsState(
                        targetValue = if (isSelected) PixelPitchBlack else PixelTextWhite,
                        animationSpec = tween(150)
                    )

                    val label = when {
                        preset == 0.5f -> ".5"
                        preset == 1.0f -> "1x"
                        preset == 1.4f -> "1.4"
                        preset == 2.0f -> "2"
                        preset == 5.0f -> "5"
                        preset == 10.0f -> "10"
                        preset == preset.toInt().toFloat() -> "${preset.toInt()}"
                        else -> "$preset"
                    }

                    Box(
                        modifier = Modifier
                            .scale(scale.value)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(btnBgColor.value)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onZoomSelected(preset)
                                isExpandedDial = true
                                lastInteractionTime = System.currentTimeMillis()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor.value,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
