package com.auracam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auracam.ui.components.pixelGlass
import com.auracam.ui.theme.AuraCamTheme
import com.auracam.ui.theme.PixelGlassBorder
import com.auracam.ui.theme.PixelPitchBlack
import com.auracam.ui.theme.PixelSurfaceContainerHigh
import com.auracam.ui.theme.PixelTextOnYellow
import com.auracam.ui.theme.PixelTextSecondary
import com.auracam.ui.theme.PixelTextWhite
import com.auracam.ui.theme.PixelYellowAccent

@Composable
fun CameraPermissionScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPitchBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .pixelGlass(
                        shape = CircleShape,
                        backgroundColor = PixelSurfaceContainerHigh,
                        borderColor = PixelGlassBorder
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (permanentlyDenied) {
                        Icons.Default.NoPhotography
                    } else {
                        Icons.Default.CameraAlt
                    },
                    contentDescription = null,
                    tint = PixelYellowAccent,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = if (permanentlyDenied) "Camera access is blocked" else "AuraCam needs your camera",
                style = AuraCamTheme.typography.headlineSmall,
                color = PixelTextWhite,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (permanentlyDenied) {
                    "Camera access was denied for this app. Turn it back on in system settings to use the viewfinder."
                } else {
                    "The viewfinder, capture and video modes all need camera access. Microphone access is optional and only used to record audio with video."
                },
                style = AuraCamTheme.typography.bodyMedium,
                color = PixelTextSecondary,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PixelYellowAccent,
                    contentColor = PixelTextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Text(
                    text = if (permanentlyDenied) "Open settings" else "Grant camera access",
                    style = AuraCamTheme.typography.labelLarge
                )
            }

            if (permanentlyDenied) {
                TextButton(onClick = onRequestPermission) {
                    Text(
                        text = "Try again",
                        style = AuraCamTheme.typography.labelLarge,
                        color = PixelTextSecondary
                    )
                }
            }
        }
    }
}
