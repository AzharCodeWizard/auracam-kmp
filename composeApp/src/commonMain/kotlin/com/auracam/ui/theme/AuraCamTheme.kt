package com.auracam.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ====================================================
// Authentic Google Pixel M3 Expressive Dark Color Scheme
// ====================================================

val PixelDarkColorScheme: ColorScheme = darkColorScheme(
    primary = PixelYellowAccent,
    onPrimary = PixelTextOnYellow,
    primaryContainer = PixelYellowContainer,
    onPrimaryContainer = PixelYellowAccent,
    inversePrimary = Color(0xFF6C5E00),

    secondary = PixelGoogleBlue,
    onSecondary = Color(0xFF003062),
    secondaryContainer = PixelBlueContainer,
    onSecondaryContainer = PixelGoogleBlue,

    tertiary = PixelLevelerGreen,
    onTertiary = Color(0xFF003919),
    tertiaryContainer = PixelGreenContainer,
    onTertiaryContainer = PixelLevelerGreen,

    background = PixelPitchBlack,
    onBackground = PixelTextPrimary,

    surface = PixelDarkSurface,
    onSurface = PixelTextPrimary,
    surfaceVariant = PixelSurfaceVariant,
    onSurfaceVariant = PixelTextSecondary,

    surfaceContainerLowest = PixelSurfaceContainerLowest,
    surfaceContainerLow = PixelSurfaceContainerLow,
    surfaceContainer = PixelSurfaceContainer,
    surfaceContainerHigh = PixelSurfaceContainerHigh,
    surfaceContainerHighest = PixelSurfaceContainerHighest,
    surfaceDim = PixelSurfaceDim,
    surfaceBright = PixelSurfaceBright,

    outline = Color(0xFF8E9099),
    outlineVariant = PixelGlassBorder,
    scrim = PixelPitchBlack,

    error = PixelRecordRed,
    onError = Color.White,
    errorContainer = PixelRedContainer,
    onErrorContainer = PixelRecordRed
)

@Composable
fun AuraCamTheme(
    content: @Composable () -> Unit
) {
    val cameraTypography = CameraTypography()
    val cameraShapes = CameraShapes()

    CompositionLocalProvider(
        LocalCameraTypography provides cameraTypography,
        LocalCameraShapes provides cameraShapes
    ) {
        MaterialTheme(
            colorScheme = PixelDarkColorScheme,
            typography = AuraCamTypography,
            shapes = AuraCamShapes,
            content = content
        )
    }
}

object AuraCamTheme {
    val colors: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val cameraTypography: CameraTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalCameraTypography.current

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes

    val cameraShapes: CameraShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalCameraShapes.current
}
