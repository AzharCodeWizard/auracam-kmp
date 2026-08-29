package com.auracam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PixelDarkBackground = Color(0xFF0E0E0E)
val PixelSurfaceDark = Color(0xFF1E1E1E)
val PixelSurfaceVariant = Color(0xFF2D2D2D)
val PixelYellowAccent = Color(0xFFFFDB58) // Pixel 8/9 Pro warm golden yellow
val PixelGoogleBlue = Color(0xFF8AB4F8)
val PixelLevelerGreen = Color(0xFF81C995)
val PixelFocusPeakingGreen = Color(0xFF00FF66)
val PixelRecordRed = Color(0xFFEA4335)
val PixelTextWhite = Color(0xFFF1F1F1)
val PixelTextMuted = Color(0xFF9E9E9E)

private val DarkColorScheme = darkColorScheme(
    primary = PixelYellowAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3B3000),
    onPrimaryContainer = PixelYellowAccent,
    secondary = PixelGoogleBlue,
    onSecondary = Color.Black,
    background = PixelDarkBackground,
    onBackground = PixelTextWhite,
    surface = PixelSurfaceDark,
    onSurface = PixelTextWhite,
    surfaceVariant = PixelSurfaceVariant,
    onSurfaceVariant = PixelTextMuted,
    error = PixelRecordRed,
    onError = Color.White
)

@Composable
fun AuraCamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
