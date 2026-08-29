package com.auracam.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ==========================================
// High-Contrast Viewfinder HUD Typography
// ==========================================

@Immutable
data class CameraTypography(
    /** Countdown timer overlay (e.g. "3", "10") with high-contrast shadow */
    val countdown: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
        color = PixelYellowAccent,
        shadow = Shadow(
            color = PixelHudShadow,
            offset = Offset(0f, 4f),
            blurRadius = 8f
        )
    ),

    /** Live HUD metrics: Shutter speed (1/1000s), ISO (ISO 400), EV (+0.7), Kelvin (5500K) */
    val hudMetric: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = PixelTextPrimary,
        shadow = Shadow(
            color = PixelHudShadow,
            offset = Offset(0f, 2f),
            blurRadius = 4f
        )
    ),

    /** Highlighted HUD metric in warm yellow */
    val hudMetricHighlight: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = PixelYellowAccent,
        shadow = Shadow(
            color = PixelHudShadow,
            offset = Offset(0f, 2f),
            blurRadius = 4f
        )
    ),

    /** Pill button label (e.g. Quick settings, Aspect ratio options) */
    val pillLabel: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
        color = PixelTextPrimary
    ),

    /** Active/selected pill button label */
    val pillLabelActive: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
        color = PixelTextOnYellow
    ),

    /** Small all-caps status badges (ULTRA HDR, RAW, PRO) */
    val badgeSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        color = PixelTextWhite
    ),

    /** Mode carousel titles (Photo, Video, Night Sight) */
    val modeTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        color = PixelTextSecondary
    ),

    /** Active mode carousel title */
    val modeTitleActive: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        color = PixelYellowAccent
    ),

    /** Horizon Leveler degree readout (e.g. "0° LEVEL") */
    val levelDegree: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
        color = PixelPitchBlack
    ),

    /** Recording time counter */
    val recordTimer: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = PixelTextWhite,
        shadow = Shadow(
            color = PixelHudShadow,
            offset = Offset(0f, 2f),
            blurRadius = 4f
        )
    )
)

val LocalCameraTypography = staticCompositionLocalOf { CameraTypography() }

// ==========================================
// Material 3 Expressive Base Typography
// ==========================================

val AuraCamTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
        color = PixelTextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        color = PixelTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = PixelTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        color = PixelTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = PixelTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = PixelTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = PixelTextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = PixelTextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = PixelTextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = PixelTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = PixelTextMuted
    )
)
