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
import androidx.compose.foundation.shape.CircleShape
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
import com.auracam.ui.theme.*

@Composable
fun ZoomSelector(
    currentZoom: Float,
    zoomPresets: List<Float>,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (zoomPresets.isEmpty()) return

    Box(
        modifier = modifier
            .pixelGlass(
                shape = CircleShape,
                backgroundColor = PixelGlassScrim,
                borderColor = PixelGlassBorder
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val selectedPreset = zoomPresets.minByOrNull { kotlin.math.abs(currentZoom - it) }

            zoomPresets.forEach { preset ->
                val isSelected = preset == selectedPreset

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
                    preset < 1.0f -> ".5"
                    preset == 1.0f -> "1x"
                    else -> "${preset.toInt()}"
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
                        ) { onZoomSelected(preset) },
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
