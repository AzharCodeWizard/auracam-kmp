package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.auracam.camera.domain.CameraEngine
import com.auracam.camera.domain.ColorProfile
import com.auracam.camera.domain.DualVlogLayout
import com.auracam.camera.domain.DualVlogNormalizedGeometry
import com.auracam.camera.domain.NormalizedRect
import com.auracam.camera.domain.TrackedSubject
import com.auracam.ui.theme.*
import kotlin.math.roundToInt

/**
 * Director-style Dual Recording chrome.
 *
 * The two camera feeds are *not* composed here. The engine's GL compositor draws both streams
 * into the single viewfinder surface underneath this layer, which is why swapping streams or
 * switching layouts is instant: nothing in this file owns a camera surface, so nothing gets
 * torn down and rebuilt when the layout changes.
 *
 * What lives here is only what the user touches — the control island, the split seam, the
 * draggable PiP frame, and the hardware tracking reticle.
 */
@Composable
fun DirectorDualRecordingOverlay(
    engine: CameraEngine,
    isRecording: Boolean,
    colorProfile: ColorProfile,
    onColorProfileSelected: (ColorProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val layout by engine.dualVlogLayout.collectAsState()
    val isSwapped by engine.isDualStreamSwapped.collectAsState()
    val pipRect by engine.dualVlogPipRect.collectAsState()
    val trackedSubjects by engine.trackedSubjects.collectAsState()
    var isFilterDrawerOpen by remember { mutableStateOf(false) }

    val frames = remember(layout, isSwapped, pipRect) {
        DualVlogNormalizedGeometry.framesFor(layout, isSwapped, pipRect)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (DualVlogNormalizedGeometry.isOverlayLayout(layout)) {
            DirectorPipFrame(
                rect = frames.let { if (it.frontIsMain) it.rear else it.front },
                isCircle = layout == DualVlogLayout.PIP_CIRCLE,
                onRectChanged = { engine.setDualVlogPipRect(it) },
                onSwapStreams = { engine.swapDualStreams() }
            )
        } else {
            DirectorSplitSeam(
                splitVertically = layout == DualVlogLayout.SIDE_BY_SIDE,
                frontIsMain = frames.frontIsMain,
                onSwapStreams = { engine.swapDualStreams() }
            )
        }

        SubjectTrackingReticle(subjects = trackedSubjects)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .zIndex(30f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DirectorControlIsland(
                activeLayout = layout,
                onLayoutSelected = { engine.setDualVlogLayout(it) },
                onSwapStreams = { engine.swapDualStreams() },
                activeFilter = colorProfile,
                isFilterDrawerOpen = isFilterDrawerOpen,
                onToggleFilterDrawer = { isFilterDrawerOpen = !isFilterDrawerOpen }
            )

            AnimatedVisibility(
                visible = isFilterDrawerOpen,
                enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 2 }
            ) {
                DirectorInlineFilterDrawer(
                    activeProfile = colorProfile,
                    onSelectProfile = {
                        onColorProfileSelected(it)
                        engine.setColorProfile(it)
                    },
                    onDismiss = { isFilterDrawerOpen = false },
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
    }
}

/**
 * R1: the seam between the two edge-to-edge halves, plus the floating swap pill and the stream
 * labels. The halves themselves are composited frames, so this draws only the divider furniture.
 */
@Composable
private fun DirectorSplitSeam(
    splitVertically: Boolean,
    frontIsMain: Boolean,
    onSwapStreams: () -> Unit
) {
    var swapRotation by remember { mutableStateOf(0f) }
    val animatedSwapAngle by animateFloatAsState(
        targetValue = swapRotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "splitSwapAngle"
    )

    val mainLabel = if (frontIsMain) "FRONT SELFIE" else "REAR MAIN"
    val insetLabel = if (frontIsMain) "REAR MAIN" else "FRONT SELFIE"

    Box(modifier = Modifier.fillMaxSize()) {
        if (splitVertically) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color(0x40FFFFFF))
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x40FFFFFF))
            )
        }

        // Labels sit at the inner edge of each half, clear of the control island at the top.
        if (splitVertically) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    StreamLabel(
                        text = mainLabel,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    StreamLabel(
                        text = insetLabel,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    StreamLabel(
                        text = mainLabel,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    StreamLabel(
                        text = insetLabel,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .pixelGlass(
                    shape = CircleShape,
                    backgroundColor = PixelGlassScrimHeavy,
                    borderColor = PixelGlassBorder
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    swapRotation += 180f
                    onSwapStreams()
                }
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .zIndex(20f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Swap feeds",
                    tint = PixelYellowAccent,
                    modifier = Modifier
                        .size(13.dp)
                        .rotate(animatedSwapAngle)
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
    }
}

/**
 * R2: the movable PiP frame.
 *
 * Dragging writes the window's normalized rect straight back to the engine, so the compositor
 * moves the actual pixels — the outline the user drags and the frame that gets recorded are the
 * same rectangle, never two approximations of each other.
 */
@Composable
private fun DirectorPipFrame(
    rect: NormalizedRect,
    isCircle: Boolean,
    onRectChanged: (NormalizedRect) -> Unit,
    onSwapStreams: () -> Unit
) {
    val density = LocalDensity.current
    var containerWidth by remember { mutableStateOf(0) }
    var containerHeight by remember { mutableStateOf(0) }
    var dragOrigin by remember { mutableStateOf<Offset?>(null) }
    var dragDistance by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                containerWidth = it.width
                containerHeight = it.height
            }
    ) {
        if (containerWidth == 0 || containerHeight == 0) return@Box

        val shape = if (isCircle) CircleShape else RoundedCornerShape(20.dp)
        val widthPx = rect.width * containerWidth
        val heightPx = rect.height * containerHeight
        val offsetX = rect.left * containerWidth
        val offsetY = rect.top * containerHeight

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(
                    with(density) { widthPx.toDp() },
                    with(density) { heightPx.toDp() }
                )
                .clip(shape)
                .border(1.5.dp, Color(0x66FFFFFF), shape)
                .zIndex(25f)
                .pointerInput(containerWidth, containerHeight) {
                    detectDragGestures(
                        onDragStart = {
                            dragOrigin = Offset(rect.left, rect.top)
                            dragDistance = 0f
                        },
                        onDragEnd = {
                            val origin = dragOrigin
                            dragOrigin = null
                            if (dragDistance < 12f) {
                                onSwapStreams()
                            } else if (origin != null) {
                                // Settle into the nearest magnetic corner.
                                val current = NormalizedRect(
                                    origin.x, origin.y,
                                    origin.x + rect.width, origin.y + rect.height
                                )
                                onRectChanged(
                                    DualVlogNormalizedGeometry.pipRectFor(
                                        DualVlogNormalizedGeometry.nearestCorner(current),
                                        widthFraction = rect.width,
                                        heightFraction = rect.height
                                    )
                                )
                            }
                        },
                        onDragCancel = { dragOrigin = null },
                        onDrag = { change, amount ->
                            change.consume()
                            dragDistance += kotlin.math.hypot(amount.x, amount.y)
                            val origin = dragOrigin ?: Offset(rect.left, rect.top)
                            val nextLeft = origin.x + amount.x / containerWidth
                            val nextTop = origin.y + amount.y / containerHeight
                            dragOrigin = Offset(nextLeft, nextTop)
                            onRectChanged(
                                NormalizedRect(
                                    nextLeft,
                                    nextTop,
                                    nextLeft + rect.width,
                                    nextTop + rect.height
                                )
                            )
                        }
                    )
                }
        )
    }
}

/**
 * Draws the hardware tracker's current subject box.
 *
 * These rectangles come from the camera HAL's own face detection via `CaptureResult`, and the
 * same box is fed back as the AF/AE metering region — so the reticle marks what the sensor is
 * actually focusing and metering on, not a separate software guess.
 */
@Composable
private fun SubjectTrackingReticle(
    subjects: List<TrackedSubject>
) {
    if (subjects.isEmpty()) return
    var containerWidth by remember { mutableStateOf(0) }
    var containerHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                containerWidth = it.width
                containerHeight = it.height
            }
    ) {
        if (containerWidth == 0 || containerHeight == 0) return@Box
        subjects.take(MAX_TRACKED_RETICLES).forEach { subject ->
            val bounds = subject.bounds
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (bounds.left * containerWidth).roundToInt(),
                            (bounds.top * containerHeight).roundToInt()
                        )
                    }
                    .size(
                        with(density) { (bounds.width * containerWidth).toDp() },
                        with(density) { (bounds.height * containerHeight).toDp() }
                    )
                    .border(1.5.dp, PixelYellowAccent.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .zIndex(26f)
            )
        }
    }
}

private const val MAX_TRACKED_RETICLES = 3

@Composable
private fun StreamLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = PixelTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Top Director Control Island: Frosted glass capsule with Segmented Layout Toggle,
 * Stream Swap button, and Live Tone Filter trigger.
 */
@Composable
fun DirectorControlIsland(
    activeLayout: DualVlogLayout,
    onLayoutSelected: (DualVlogLayout) -> Unit,
    onSwapStreams: () -> Unit,
    activeFilter: ColorProfile,
    isFilterDrawerOpen: Boolean,
    onToggleFilterDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var swapRotation by remember { mutableStateOf(0f) }
    val animatedSwapAngle by animateFloatAsState(
        targetValue = swapRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    Row(
        modifier = modifier
            .pixelGlass(
                shape = CircleShape,
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // A. Layout Toggle: [ 🌓 Split | 🔲 PiP ]
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x2BFFFFFF))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Split 50/50 option
            val isSplit = activeLayout == DualVlogLayout.SPLIT_50_50
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSplit) PixelYellowAccent else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onLayoutSelected(DualVlogLayout.SPLIT_50_50) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Splitscreen,
                        contentDescription = "Split 50/50",
                        tint = if (isSplit) PixelPitchBlack else PixelTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Split",
                        color = if (isSplit) PixelPitchBlack else PixelTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSplit) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // PiP Window option
            val isPip = activeLayout == DualVlogLayout.PIP_RECT || activeLayout == DualVlogLayout.PIP_CIRCLE
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isPip) PixelYellowAccent else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onLayoutSelected(DualVlogLayout.PIP_RECT) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropPortrait,
                        contentDescription = "PiP Window",
                        tint = if (isPip) PixelPitchBlack else PixelTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "PiP",
                        color = if (isPip) PixelPitchBlack else PixelTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isPip) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Divider separator
        Text(
            text = "·",
            color = Color(0x66FFFFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        // B. Stream Swap: [ ⇄ Swap ]
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    swapRotation += 180f
                    onSwapStreams()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cached,
                contentDescription = "Swap Streams",
                tint = PixelTextWhite,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(animatedSwapAngle)
            )
            Text(
                text = "Swap",
                color = PixelTextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Divider separator
        Text(
            text = "·",
            color = Color(0x66FFFFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        // C. Live Tone Filter: [ ✨ Filter ]
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isFilterDrawerOpen) PixelYellowAccent.copy(alpha = 0.25f) else Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleFilterDrawer() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = "Live Tone Filter",
                tint = if (isFilterDrawerOpen) PixelYellowAccent else PixelTextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = activeFilter.label,
                color = if (isFilterDrawerOpen) PixelYellowAccent else PixelTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Inline Live Tone Filter Drawer for quick director tone grading.
 */
@Composable
fun DirectorInlineFilterDrawer(
    activeProfile: ColorProfile,
    onSelectProfile: (ColorProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pixelGlass(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = PixelYellowAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Director Live Tone Filter",
                        style = AuraCamTheme.typography.titleSmall,
                        color = PixelTextWhite,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Filter Drawer",
                        tint = PixelTextWhite,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Horizontal Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ColorProfile.values()) { profile ->
                    val isSelected = profile == activeProfile
                    val gradient = filterGradientFor(profile)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectProfile(profile) }
                            .padding(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(gradient))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) PixelYellowAccent else Color(0x33FFFFFF),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(PixelYellowAccent)
                                )
                            }
                        }

                        Text(
                            text = profile.label,
                            color = if (isSelected) PixelYellowAccent else PixelTextWhite,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun filterGradientFor(profile: ColorProfile): List<Color> = when (profile) {
    ColorProfile.NATURAL -> listOf(Color(0xFF888888), Color(0xFFCCCCCC))
    ColorProfile.REAL_TONE -> listOf(Color(0xFF8D6E63), Color(0xFFFFCC80))
    ColorProfile.VIBRANT -> listOf(Color(0xFFFF5722), Color(0xFFFFEB3B), Color(0xFF00E676))
    ColorProfile.CINEMATIC_WARM -> listOf(Color(0xFFE65100), Color(0xFFFFB74D), Color(0xFF4E342E))
    ColorProfile.HIGH_CONTRAST_MONO -> listOf(Color(0xFF111111), Color(0xFFEEEEEE))
    ColorProfile.VINTAGE_FILM -> listOf(Color(0xFF5D4037), Color(0xFFD7CCC8), Color(0xFFFFE082))
    ColorProfile.COOL_BREEZE -> listOf(Color(0xFF0D47A1), Color(0xFF42A5F5), Color(0xFFE0F7FA))
    ColorProfile.ASTRO_BOOST -> listOf(Color(0xFF0A0E27), Color(0xFF4A148C), Color(0xFF7C4DFF))
    ColorProfile.CLEAN_DOC -> listOf(Color(0xFF263238), Color(0xFFECEFF1))
}
