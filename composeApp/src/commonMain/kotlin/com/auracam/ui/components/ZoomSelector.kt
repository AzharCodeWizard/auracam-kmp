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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
 * Ultra-fluid iPhone-style Rotary Ticker Zoom Dial + Compact Preset Pills
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

    // Auto collapse after 3.5s of no interaction
    LaunchedEffect(lastInteractionTime, isExpandedDial) {
        if (isExpandedDial && lastInteractionTime > 0) {
            delay(3500)
            isExpandedDial = false
        }
    }

    val minZoom = availablePresets.firstOrNull() ?: 0.5f
    val maxZoom = availablePresets.lastOrNull()?.coerceAtLeast(10.0f) ?: 10.0f

    var dragZoomTracker by remember { mutableStateOf(currentZoom) }
    LaunchedEffect(currentZoom) {
        dragZoomTracker = currentZoom
    }

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
                    .fillMaxWidth(0.94f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xF0161920))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                    .padding(vertical = 10.dp, horizontal = 14.dp)
            ) {
                // Top Row: Active Zoom Readout & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(28.dp))

                    // Active zoom badge pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(PixelYellowAccent)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
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

                    // Close dial button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .clickable { isExpandedDial = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dial",
                            tint = PixelTextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Continuous Horizontal Ticker Wheel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    dragZoomTracker = currentZoom
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    lastInteractionTime = System.currentTimeMillis()
                                    // Natural intuitive direction: drag right increases zoom, drag left decreases
                                    val sensitivity = 0.010f * dragZoomTracker.coerceAtLeast(0.8f)
                                    val delta = dragAmount * sensitivity
                                    val newZoom = (dragZoomTracker + delta).coerceIn(minZoom, maxZoom)
                                    dragZoomTracker = newZoom
                                    val roundedZoom = (newZoom * 10f).roundToInt() / 10f
                                    if (roundedZoom != currentZoom) {
                                        onZoomSelected(roundedZoom)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midX = w / 2f
                        val tickSpacing = 16.dp.toPx()

                        // Center yellow indicator needle
                        drawLine(
                            color = PixelYellowAccent,
                            start = Offset(midX, 2f),
                            end = Offset(midX, h - 2f),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Relative offset based on zoom value (1 tick = 0.1x zoom)
                        val totalTicksFromMin = (currentZoom - minZoom) * 10f
                        val pixelOffset = totalTicksFromMin * tickSpacing

                        val minVisibleIndex = ((pixelOffset - midX) / tickSpacing).toInt() - 2
                        val maxVisibleIndex = ((pixelOffset + midX) / tickSpacing).toInt() + 2

                        for (index in minVisibleIndex..maxVisibleIndex) {
                            val tickX = midX + (index * tickSpacing) - pixelOffset
                            if (tickX in 0f..w) {
                                val tickZoom = minZoom + (index / 10f)
                                if (tickZoom in minZoom..maxZoom) {
                                    val distFromCenter = abs(tickX - midX) / (w / 2f)
                                    val alpha = (1f - distFromCenter * 0.75f).coerceIn(0.15f, 0.95f)

                                    val isMajor = (index % 10 == 0) || abs(tickZoom - 0.5f) < 0.05f
                                    val isMedium = index % 5 == 0 && !isMajor
                                    val tickHeight = when {
                                        isMajor -> h * 0.65f
                                        isMedium -> h * 0.45f
                                        else -> h * 0.28f
                                    }
                                    val topY = (h - tickHeight) / 2f

                                    drawLine(
                                        color = if (isMajor) Color.White.copy(alpha = alpha) else Color(0xFFAAAAAA).copy(alpha = alpha),
                                        start = Offset(tickX, topY),
                                        end = Offset(tickX, topY + tickHeight),
                                        strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Preset Shortcut Row inside the dial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availablePresets.forEach { preset ->
                        val isSelected = abs(currentZoom - preset) < 0.15f
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) PixelYellowAccent else Color(0x33FFFFFF))
                                .clickable {
                                    onZoomSelected(preset)
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when {
                                    preset == 0.5f -> ".5"
                                    preset == 1.0f -> "1x"
                                    preset == preset.toInt().toFloat() -> "${preset.toInt()}x"
                                    else -> "${preset}x"
                                },
                                color = if (isSelected) PixelPitchBlack else PixelTextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                .pointerInput(currentZoom) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isExpandedDial = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            lastInteractionTime = System.currentTimeMillis()
                            val sensitivity = 0.015f * currentZoom.coerceAtLeast(0.8f)
                            val delta = dragAmount * sensitivity
                            val newZoom = (currentZoom + delta).coerceIn(minZoom, maxZoom)
                            val roundedZoom = (newZoom * 10f).roundToInt() / 10f
                            if (roundedZoom != currentZoom) {
                                onZoomSelected(roundedZoom)
                            }
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
