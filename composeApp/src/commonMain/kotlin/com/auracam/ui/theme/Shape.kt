package com.auracam.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ==========================================
// Google Pixel M3 Expressive Shapes
// ==========================================

@Immutable
data class CameraShapes(
    /** Complete round pill shape for action buttons, zoom selector, quick settings */
    val pill: Shape = CircleShape,

    /** Viewfinder aspect ratio viewport smooth clipping */
    val viewport: Shape = RoundedCornerShape(28.dp),

    /** Bottom sheet top rounded corners */
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),

    /** Quick settings dialog and modal cards */
    val card: Shape = RoundedCornerShape(24.dp),

    /** Pro controls tab chips and segmented buttons */
    val chip: Shape = RoundedCornerShape(16.dp),

    /** Micro badges and status indicator pills */
    val badge: Shape = RoundedCornerShape(8.dp),

    /** Tactile slider thumbs and small items */
    val smallPill: Shape = RoundedCornerShape(12.dp)
)

val LocalCameraShapes = staticCompositionLocalOf { CameraShapes() }

val AuraCamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
