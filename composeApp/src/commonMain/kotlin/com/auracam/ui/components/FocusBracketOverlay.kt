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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
 * Ultra-Clean Minimalist Autofocus Reticle with Auto-Fading Glass EV Slider
 */
@Composable
fun FocusBracketOverlay(
    focusPoint: FocusPoint?,
    proSettings: ProSettings,
    onProSettingsChange: ((ProSettings) -> ProSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    var isVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var isDraggingEv by remember { mutableStateOf(false) }

    // Re-trigger snap on new focus point
    LaunchedEffect(focusPoint) {
        isVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    // Auto fade after 2 seconds unless actively dragging EV
    LaunchedEffect(lastInteraction, isDraggingEv) {
        if (!isDraggingEv) {
            delay(2000)
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(400)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val focusPxX = maxWidth * focusPoint.x
            val focusPxY = maxHeight * focusPoint.y

            // Spring scale entrance for focus lock snap
            val snapScale = remember(focusPoint) { Animatable(1.28f) }
            LaunchedEffect(focusPoint) {
                snapScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }

            // 1. Sleek Minimalist Corner Bracket Reticle
            Box(
                modifier = Modifier
                    .offset(
                        x = (focusPxX - 28.dp).coerceIn(8.dp, maxWidth - 64.dp),
                        y = (focusPxY - 28.dp).coerceIn(8.dp, maxHeight - 64.dp)
                    )
                    .size(56.dp)
                    .scale(snapScale.value)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val bracketLen = 10.dp.toPx()
                    val strokeW = 1.6.dp.toPx()
                    val cornerRadius = 3.dp.toPx()
                    val color = PixelYellowAccent

                    // Top-Left corner
                    drawLine(color, Offset(0f, bracketLen), Offset(0f, cornerRadius), strokeW, StrokeCap.Round)
                    drawLine(color, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW, StrokeCap.Round)

                    // Top-Right corner
                    drawLine(color, Offset(w - bracketLen, 0f), Offset(w, 0f), strokeW, StrokeCap.Round)
                    drawLine(color, Offset(w, 0f), Offset(w, bracketLen), strokeW, StrokeCap.Round)

                    // Bottom-Left corner
                    drawLine(color, Offset(0f, h - bracketLen), Offset(0f, h), strokeW, StrokeCap.Round)
                    drawLine(color, Offset(0f, h), Offset(bracketLen, h), strokeW, StrokeCap.Round)

                    // Bottom-Right corner
                    drawLine(color, Offset(w - bracketLen, h), Offset(w, h), strokeW, StrokeCap.Round)
                    drawLine(color, Offset(w, h - bracketLen), Offset(w, h), strokeW, StrokeCap.Round)

                    // Center subtle micro-dot
                    drawCircle(color = color, radius = 1.8.dp.toPx(), center = Offset(w / 2f, h / 2f))
                }
            }

            // 2. Sleek Glass EV Exposure Bias Slider
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .offset(
                        x = (focusPxX + 34.dp).coerceIn(8.dp, maxWidth - 44.dp),
                        y = (focusPxY - 50.dp).coerceIn(8.dp, maxHeight - 120.dp)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xD9101216))
                    .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDraggingEv = true
                                lastInteraction = System.currentTimeMillis()
                            },
                            onDragEnd = {
                                isDraggingEv = false
                                lastInteraction = System.currentTimeMillis()
                            },
                            onDragCancel = {
                                isDraggingEv = false
                                lastInteraction = System.currentTimeMillis()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                lastInteraction = System.currentTimeMillis()
                                val deltaEv = -dragAmount / 30f
                                onProSettingsChange { current ->
                                    val newEv = (current.evBias + deltaEv).coerceIn(-3.0f, 3.0f)
                                    current.copy(evBias = kotlin.math.round(newEv * 10) / 10f)
                                }
                            }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Exposure Compensation",
                    tint = PixelYellowAccent,
                    modifier = Modifier.size(13.dp)
                )

                // Slider Track
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbOffset = (-proSettings.evBias * 8).dp.coerceIn(-22.dp, 22.dp)
                    Box(
                        modifier = Modifier
                            .offset(y = thumbOffset)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(PixelYellowAccent)
                    )
                }

                // Numeric EV Readout (only when non-zero)
                if (proSettings.evBias != 0.0f) {
                    Text(
                        text = "${if (proSettings.evBias > 0) "+" else ""}${proSettings.evBias}",
                        color = PixelYellowAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
