package com.auracam.ui.components

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.DualVlogLayout
import com.auracam.ui.theme.*
import kotlin.math.roundToInt

/**
 * Multi-Stream Director's View / Dual Vlogger Mode
 * Displays concurrent Main + Selfie feeds with draggable PiP and 50/50 split modes.
 */
@Composable
fun DualVlogOverlay(
    isRecording: Boolean,
    onFlipStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    var layout by remember { mutableStateOf(DualVlogLayout.PIP_RECT) }
    var pipOffset by remember { mutableStateOf(Offset(24f, 24f)) }
    var swapped by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Top Director HUD Badges
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PixelRecordRed)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = PixelTextWhite,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "DUAL VLOG",
                        color = PixelTextWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Layout Mode Toggle Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x99181B20))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable {
                        layout = when (layout) {
                            DualVlogLayout.PIP_RECT -> DualVlogLayout.PIP_CIRCLE
                            DualVlogLayout.PIP_CIRCLE -> DualVlogLayout.SPLIT_50_50
                            DualVlogLayout.SPLIT_50_50 -> DualVlogLayout.PIP_RECT
                            else -> DualVlogLayout.PIP_RECT
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = layout.label,
                    color = PixelYellowAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Draggable Floating PiP Selfie Feed (when in PiP mode)
        if (layout == DualVlogLayout.PIP_RECT || layout == DualVlogLayout.PIP_CIRCLE) {
            val isCircle = layout == DualVlogLayout.PIP_CIRCLE
            val pipShape = if (isCircle) CircleShape else RoundedCornerShape(18.dp)

            Box(
                modifier = Modifier
                    .offset { IntOffset(pipOffset.x.roundToInt(), pipOffset.y.roundToInt()) }
                    .size(if (isCircle) 120.dp else 110.dp, if (isCircle) 120.dp else 150.dp)
                    .clip(pipShape)
                    .background(Color(0xFF1E222A))
                    .border(2.5.dp, PixelYellowAccent, pipShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffset = Offset(
                                x = (pipOffset.x + dragAmount.x).coerceIn(16f, 750f),
                                y = (pipOffset.y + dragAmount.y).coerceIn(16f, 1400f)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Secondary Stream Representation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (swapped) "REAR FEED" else "SELFIE FEED",
                        color = PixelYellowAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "LIVE 60FPS",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    )
                }

                // Swap Button inside PiP
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .clickable {
                            swapped = !swapped
                            onFlipStream()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Swap Feeds",
                        tint = PixelYellowAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else if (layout == DualVlogLayout.SPLIT_50_50) {
            // Split Line Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(PixelYellowAccent)
                    .align(Alignment.Center)
            )
        }
    }
}
