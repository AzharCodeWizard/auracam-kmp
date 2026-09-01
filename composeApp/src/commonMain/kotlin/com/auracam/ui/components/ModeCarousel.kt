package com.auracam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Editorial Flagship Mode Carousel with Crisp Illuminated Accent
 */
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
            CameraMode.SLOW_MOTION,
            CameraMode.TIME_LAPSE,
            CameraMode.DUAL_VLOG,
            CameraMode.CINEMATIC,
            CameraMode.PRO,
            CameraMode.ASTRO,
            CameraMode.LONG_EXPOSURE
        )
    }

    val listState = rememberLazyListState()

    // Auto-scroll center active mode
    LaunchedEffect(currentMode) {
        val index = modes.indexOf(currentMode)
        if (index >= 0) {
            listState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 48.dp)
        ) {
            itemsIndexed(modes) { index, mode ->
                val isSelected = mode == currentMode

                val scale = animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                val textColor = animateColorAsState(
                    targetValue = if (isSelected) PixelYellowAccent else Color(0xFF8E929E),
                    animationSpec = tween(durationMillis = 180)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .scale(scale.value)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeSelected(mode) }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mode.displayName.uppercase(),
                        color = textColor.value,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    )

                    // Luminous accent dot indicator under selected mode
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 4.dp else 0.dp)
                            .clip(CircleShape)
                            .background(PixelYellowAccent)
                    )
                }
            }
        }
    }
}
