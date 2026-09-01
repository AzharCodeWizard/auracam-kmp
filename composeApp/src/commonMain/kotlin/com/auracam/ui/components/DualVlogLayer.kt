package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.auracam.camera.domain.CameraEngine
import com.auracam.camera.domain.DualVlogLayout
import com.auracam.ui.theme.*
import kotlin.math.roundToInt

/**
 * Ultra-Clean Multi-Stream Director / Vlog Mode
 * Features floating corner-snapping PiP, seamless 50/50 split, and zero visual clutter.
 */
@Composable
fun DualVlogOverlay(
    engine: CameraEngine,
    isRecording: Boolean,
    onFlipStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    var layout by remember { mutableStateOf(DualVlogLayout.PIP_RECT) }
    var targetOffset by remember { mutableStateOf(Offset(20f, 110f)) }
    var rawDragOffset by remember { mutableStateOf(Offset(20f, 110f)) }
    var isDragging by remember { mutableStateOf(false) }
    var swapped by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }

    // Smooth spring animation to snapped corner
    val animatedOffset by animateOffsetAsState(
        targetValue = if (isDragging) rawDragOffset else targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxX = (maxWidth.value * 2.5f).coerceAtLeast(600f)
        val maxY = (maxHeight.value * 2.5f).coerceAtLeast(1000f)

        if (layout == DualVlogLayout.SPLIT_50_50) {
            // 1. Cinematic 50/50 Split View
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Stream (Primary)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Minimalist Frosted Glass Split Divider with Micro-Swap Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0x40FFFFFF))
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xCC12141A))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                swapped = !swapped
                                onFlipStream()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cached,
                                contentDescription = "Swap Streams",
                                tint = PixelYellowAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "SWAP",
                                color = PixelYellowAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0x40FFFFFF))
                    )
                }

                // Bottom Stream (Secondary Concurrent Camera Feed)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    SecondaryCameraPreview(
                        engine = engine,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // 2. Floating Draggable Corner-Snapping PiP View
            val isCircle = layout == DualVlogLayout.PIP_CIRCLE
            val pipShape = if (isCircle) CircleShape else RoundedCornerShape(22.dp)
            val pipWidth = if (isCircle) 124.dp else 114.dp
            val pipHeight = if (isCircle) 124.dp else 152.dp

            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffset.x.roundToInt(), animatedOffset.y.roundToInt()) }
                    .size(pipWidth, pipHeight)
                    .shadow(12.dp, pipShape)
                    .clip(pipShape)
                    .background(Color.Black)
                    .border(1.5.dp, Color(0x66FFFFFF), pipShape)
            ) {
                // Secondary Live Stream Preview
                SecondaryCameraPreview(
                    engine = engine,
                    modifier = Modifier.fillMaxSize()
                )

                // Drag gesture handling with smart corner snapping
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    rawDragOffset = targetOffset
                                },
                                onDragEnd = {
                                    isDragging = false
                                    // Snap to nearest corner (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
                                    val snapLeft = rawDragOffset.x < 360f
                                    val snapTop = rawDragOffset.y < 650f
                                    val targetX = if (snapLeft) 20f else (maxX - 280f).coerceAtLeast(400f)
                                    val targetY = if (snapTop) 110f else (maxY - 420f).coerceAtLeast(700f)
                                    targetOffset = Offset(targetX, targetY)
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    rawDragOffset = Offset(
                                        x = (rawDragOffset.x + dragAmount.x).coerceIn(10f, maxX),
                                        y = (rawDragOffset.y + dragAmount.y).coerceIn(60f, maxY)
                                    )
                                }
                            )
                        }
                )

                // Subtle Swap Icon Button in bottom-right of PiP
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            swapped = !swapped
                            onFlipStream()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cached,
                        contentDescription = "Swap Camera Feeds",
                        tint = PixelTextWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 3. Top Minimalist Vlog Director Control Island
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .zIndex(20f)
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xD9101216))
                    .border(1.dp, Color(0x2BFFFFFF), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Layout Switcher Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            layout = when (layout) {
                                DualVlogLayout.PIP_RECT -> DualVlogLayout.PIP_CIRCLE
                                DualVlogLayout.PIP_CIRCLE -> DualVlogLayout.SPLIT_50_50
                                DualVlogLayout.SPLIT_50_50 -> DualVlogLayout.PIP_RECT
                                else -> DualVlogLayout.PIP_RECT
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when (layout) {
                            DualVlogLayout.PIP_RECT -> Icons.Default.CropSquare
                            DualVlogLayout.PIP_CIRCLE -> Icons.Default.CropLandscape
                            DualVlogLayout.SPLIT_50_50 -> Icons.Default.Splitscreen
                            else -> Icons.Default.CropSquare
                        },
                        contentDescription = null,
                        tint = PixelYellowAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = when (layout) {
                            DualVlogLayout.PIP_RECT -> "PiP Window"
                            DualVlogLayout.PIP_CIRCLE -> "Circle PiP"
                            DualVlogLayout.SPLIT_50_50 -> "Split 50/50"
                            else -> "Dual View"
                        },
                        color = PixelTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Divider bullet
                Text(
                    text = "·",
                    color = Color(0x4DFFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Quick Swap Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            swapped = !swapped
                            onFlipStream()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cached,
                        contentDescription = "Swap Feeds",
                        tint = PixelTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Swap",
                        color = PixelTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
