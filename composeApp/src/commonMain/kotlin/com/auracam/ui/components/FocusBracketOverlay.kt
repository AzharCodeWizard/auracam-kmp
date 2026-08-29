package com.auracam.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Contrast
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
import com.auracam.ui.theme.PixelYellowAccent

@Composable
fun FocusBracketOverlay(
    focusPoint: FocusPoint?,
    proSettings: ProSettings,
    onProSettingsChange: ((ProSettings) -> ProSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val focusPxX = maxWidth * focusPoint.x
        val focusPxY = maxHeight * focusPoint.y

        // Yellow Corner Focus Bracket
        Box(
            modifier = Modifier
                .offset(
                    x = (focusPxX - 36.dp).coerceIn(0.dp, maxWidth - 72.dp),
                    y = (focusPxY - 36.dp).coerceIn(0.dp, maxHeight - 72.dp)
                )
                .size(72.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 2.5f.dp.toPx()
                val bracketLen = 14.dp.toPx()
                val c = PixelYellowAccent.copy(alpha = pulseAlpha)

                // Top-Left
                drawLine(c, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW)
                drawLine(c, Offset(0f, 0f), Offset(0f, bracketLen), strokeW)

                // Top-Right
                drawLine(c, Offset(size.width, 0f), Offset(size.width - bracketLen, 0f), strokeW)
                drawLine(c, Offset(size.width, 0f), Offset(size.width, bracketLen), strokeW)

                // Bottom-Left
                drawLine(c, Offset(0f, size.height), Offset(bracketLen, size.height), strokeW)
                drawLine(c, Offset(0f, size.height), Offset(0f, size.height - bracketLen), strokeW)

                // Bottom-Right
                drawLine(c, Offset(size.width, size.height), Offset(size.width - bracketLen, size.height), strokeW)
                drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), strokeW)

                // Center metering cross
                drawCircle(c, radius = 4.dp.toPx(), style = Stroke(width = strokeW))
            }
        }

        // Dual Exposure Sliders Box (Brightness + Shadows)
        Row(
            modifier = Modifier
                .offset(
                    x = (focusPxX + 44.dp).coerceIn(0.dp, maxWidth - 88.dp),
                    y = (focusPxY - 60.dp).coerceIn(0.dp, maxHeight - 140.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xCC181818))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Brightness / EV Slider
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaEv = -dragAmount / 30f
                        onProSettingsChange { current ->
                            val newEv = (current.evBias + deltaEv).coerceIn(-3.0f, 3.0f)
                            current.copy(evBias = kotlin.math.round(newEv * 10) / 10f)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Brightness5,
                    contentDescription = "Brightness",
                    tint = PixelYellowAccent,
                    modifier = Modifier.size(16.dp)
                )

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF444444)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-proSettings.evBias * 10).dp.coerceIn(-32.dp, 32.dp))
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(PixelYellowAccent)
                    )
                }

                Text(
                    text = "${if (proSettings.evBias >= 0) "+" else ""}${proSettings.evBias}",
                    color = PixelYellowAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 2. Shadows Tone Slider
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaShadow = -dragAmount / 50f
                        onProSettingsChange { current ->
                            val newShadow = (current.shadowBias + deltaShadow).coerceIn(-1.0f, 1.0f)
                            current.copy(shadowBias = kotlin.math.round(newShadow * 10) / 10f)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Contrast,
                    contentDescription = "Shadows",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF444444)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-proSettings.shadowBias * 30).dp.coerceIn(-32.dp, 32.dp))
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                Text(
                    text = "${if (proSettings.shadowBias >= 0) "+" else ""}${proSettings.shadowBias}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
