package com.auracam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.CameraMode
import com.auracam.ui.theme.*

@Composable
fun ModeCarousel(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = remember {
        listOf(
            CameraMode.NIGHT_SIGHT,
            CameraMode.PORTRAIT,
            CameraMode.PHOTO,
            CameraMode.VIDEO,
            CameraMode.DUAL_VLOG,
            CameraMode.CINEMATIC,
            CameraMode.PRO,
            CameraMode.ASTRO,
            CameraMode.LONG_EXPOSURE
        )
    }

    val listState = rememberLazyListState()

    // Smooth auto-scroll centering to active mode
    LaunchedEffect(currentMode) {
        val index = modes.indexOf(currentMode)
        if (index >= 0) {
            listState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 32.dp)
        ) {
            itemsIndexed(modes) { index, mode ->
                val isSelected = mode == currentMode

                val scale = animateFloatAsState(
                    targetValue = if (isSelected) 1.06f else 0.94f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                val textColor = animateColorAsState(
                    targetValue = if (isSelected) PixelPitchBlack else PixelTextSecondary,
                    animationSpec = tween(durationMillis = 180)
                )

                val bgColor = animateColorAsState(
                    targetValue = if (isSelected) PixelYellowAccent else Color(0x331E1E1E),
                    animationSpec = tween(durationMillis = 180)
                )

                val borderColor = animateColorAsState(
                    targetValue = if (isSelected) PixelYellowAccent else Color(0x22FFFFFF),
                    animationSpec = tween(durationMillis = 180)
                )

                Box(
                    modifier = Modifier
                        .scale(scale.value)
                        .clip(CircleShape)
                        .background(bgColor.value)
                        .border(1.dp, borderColor.value, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeSelected(mode) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.displayName,
                        color = textColor.value,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
