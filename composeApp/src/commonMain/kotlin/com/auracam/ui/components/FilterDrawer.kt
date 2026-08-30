package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.ColorProfile
import com.auracam.ui.theme.*

/**
 * Interactive Live Camera Filter / LUT Drawer
 */
@Composable
fun FilterDrawer(
    currentColorProfile: ColorProfile,
    onColorProfileSelected: (ColorProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pixelGlass(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Filters",
                        tint = PixelYellowAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Camera Filters & Tone LUTs",
                        style = AuraCamTheme.typography.titleSmall,
                        color = PixelTextWhite
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PixelTextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Horizontal Filter Thumbnail Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ColorProfile.values()) { profile ->
                    val isSelected = profile == currentColorProfile
                    val gradient = filterGradientFor(profile)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onColorProfileSelected(profile) }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(gradient))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) PixelYellowAccent else Color(0x33FFFFFF),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(PixelYellowAccent)
                                )
                            }
                        }

                        Text(
                            text = profile.label,
                            color = if (isSelected) PixelYellowAccent else PixelTextWhite,
                            fontSize = 11.sp,
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
