package com.auracam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.ui.theme.*

// =========================================================================
// Pixel Frosted Glass Modifier & Components (Google Pixel Camera Aesthetic)
// =========================================================================

/**
 * Applies authentic Pixel frosted glass styling:
 * - Geometric shape clipping
 * - Translucent tinted glass scrim background
 * - Subtle 1dp border outline
 * - Optional top specular highlight gradient for physical glass realism
 */
fun Modifier.pixelGlass(
    shape: Shape = CircleShape,
    backgroundColor: Color = PixelGlassScrim,
    borderColor: Color = PixelGlassBorder,
    borderWidth: Dp = 1.dp,
    topHighlight: Boolean = true
): Modifier = this
    .clip(shape)
    .background(backgroundColor)
    .then(
        if (topHighlight) {
            Modifier.drawWithContent {
                drawContent()
                // Top subtle specular glass edge highlight
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PixelGlassHighlight.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.4f
                    )
                )
            }
        } else {
            Modifier
        }
    )
    .border(borderWidth, borderColor, shape)

/**
 * Pixel Frosted Glass Pill Container (for HUD badges, top action bar items, zoom capsules)
 */
@Composable
fun PixelGlassPill(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    backgroundColor: Color = PixelGlassScrim,
    borderColor: Color = PixelGlassBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .pixelGlass(
                shape = shape,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                borderWidth = borderWidth
            )
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}

/**
 * Pixel Frosted Glass Card / Bottom Sheet Container (for quick settings, EXIF sheets, dialogs)
 */
@Composable
fun PixelGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = PixelGlassScrimHeavy,
    borderColor: Color = PixelGlassBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .pixelGlass(
                shape = shape,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                borderWidth = borderWidth
            )
            .padding(contentPadding),
        content = content
    )
}

/**
 * High-contrast Glass Status Badge (e.g. ULTRA HDR, RAW, PRO, NIGHT)
 * Optional interactive click support.
 */
@Composable
fun PixelGlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = PixelTextWhite,
    containerColor: Color = PixelGlassPill,
    borderColor: Color = PixelGlassBorderSubtle,
    shape: Shape = RoundedCornerShape(8.dp),
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(0.75.dp, borderColor, shape)
            .then(clickModifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}
