package com.auracam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.CapturedMedia
import com.auracam.processing.ComputationalPipeline
import com.auracam.ui.theme.*

@Composable
fun GalleryPreviewSheet(
    media: CapturedMedia,
    watermarkEnabled: Boolean,
    onShare: ((CapturedMedia) -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showExifInfo by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPitchBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .pixelGlass(
                        shape = CircleShape,
                        backgroundColor = PixelSurfaceContainerHigh,
                        borderColor = PixelGlassBorder
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = PixelTextWhite
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = media.fileName,
                    style = AuraCamTheme.typography.titleMedium,
                    color = PixelTextWhite
                )
                Text(
                    text = "${media.mode.displayName} • ${media.format.label}",
                    style = AuraCamTheme.cameraTypography.hudMetricHighlight,
                    color = PixelYellowAccent
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onShare?.invoke(media) },
                    modifier = Modifier
                        .size(40.dp)
                        .pixelGlass(
                            shape = CircleShape,
                            backgroundColor = PixelSurfaceContainerHigh,
                            borderColor = PixelGlassBorder
                        )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = PixelTextWhite
                )
            }

                IconButton(
                    onClick = { showExifInfo = !showExifInfo },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (showExifInfo) PixelYellowAccent else PixelSurfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "EXIF Info",
                        tint = if (showExifInfo) PixelPitchBlack else PixelTextWhite
                    )
                }
            }
        }

        // Image Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2C3E50), Color(0xFF0A0A0A))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Simulated Captured Scene Graphic
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Scene",
                    tint = PixelYellowAccent.copy(alpha = 0.7f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${media.mode.displayName} Capture",
                    style = AuraCamTheme.typography.headlineMedium,
                    color = PixelTextWhite
                )
                Text(
                    text = media.exif.resolution,
                    style = AuraCamTheme.typography.bodyMedium,
                    color = PixelTextSecondary
                )
            }

            // Pixel Watermark Pill Overlay
            if (watermarkEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .pixelGlass(
                            shape = CircleShape,
                            backgroundColor = PixelGlassScrimHeavy,
                            borderColor = PixelGlassBorder
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = ComputationalPipeline.formatPixelWatermark(media.exif),
                        style = AuraCamTheme.cameraTypography.hudMetric,
                        color = PixelTextPrimary
                    )
                }
            }
        }

        // EXIF Metadata Sheet
        if (showExifInfo) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .pixelGlass(
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = PixelGlassScrimHeavy,
                        borderColor = PixelGlassBorder
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "EXIF & Computational Details",
                        style = AuraCamTheme.cameraTypography.pillLabel,
                        color = PixelYellowAccent,
                        fontWeight = FontWeight.Bold
                    )

                    ExifRow("Device", media.exif.deviceModel)
                    ExifRow("Lens / Aperture", "${media.exif.lensFocalLength} (${media.exif.aperture})")
                    ExifRow("Shutter Speed", media.exif.shutterSpeed)
                    ExifRow("ISO Sensitivity", "ISO ${media.exif.iso}")
                    ExifRow("Exposure Bias", media.exif.exposureBias)
                    ExifRow("White Balance", media.exif.whiteBalance)
                    ExifRow("Format / Demosaic", media.exif.format)
                    ExifRow("Resolution", media.exif.resolution)
                    ExifRow("Captured At", media.exif.timestamp)
                    media.exif.location?.let { ExifRow("GPS Coordinates", it) }
                }
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = AuraCamTheme.typography.bodySmall, color = PixelTextSecondary)
        Text(text = value, style = AuraCamTheme.cameraTypography.hudMetric, color = PixelTextWhite)
    }
}
