package com.auracam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.auracam.ui.theme.PixelYellowAccent

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
            CameraMode.CINEMATIC,
            CameraMode.PRO,
            CameraMode.ASTRO,
            CameraMode.LONG_EXPOSURE
        )
    }

    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(modes) { mode ->
            val isSelected = mode == currentMode
            val scale = animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 0.95f,
                animationSpec = tween(durationMillis = 200)
            )
            val textColor = animateColorAsState(
                targetValue = if (isSelected) PixelYellowAccent else Color(0xFFD0D0D0),
                animationSpec = tween(durationMillis = 200)
            )
            val bgColor = animateColorAsState(
                targetValue = if (isSelected) Color(0x33FFDB58) else Color.Transparent,
                animationSpec = tween(durationMillis = 200)
            )

            Box(
                modifier = Modifier
                    .scale(scale.value)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor.value)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
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
