package com.auracam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.*
import com.auracam.ui.theme.*

@Composable
fun QuickSettingsDialog(
    mode: CameraMode,
    aspectRatio: AspectRatio,
    photoResolution: PhotoResolution,
    videoResolution: VideoResolution,
    timerDuration: TimerDuration,
    flashMode: FlashMode,
    captureFormat: CaptureFormat,
    colorProfile: ColorProfile,
    gridType: GridType,
    ultraHdr: Boolean,
    watermarkEnabled: Boolean,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onPhotoResolutionChange: (PhotoResolution) -> Unit,
    onVideoResolutionChange: (VideoResolution) -> Unit,
    onTimerChange: (TimerDuration) -> Unit,
    onFlashChange: (FlashMode) -> Unit,
    onCaptureFormatChange: (CaptureFormat) -> Unit,
    onColorProfileChange: (ColorProfile) -> Unit,
    onGridChange: (GridType) -> Unit,
    onUltraHdrToggle: (Boolean) -> Unit,
    onWatermarkToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideoMode = mode == CameraMode.VIDEO || mode == CameraMode.CINEMATIC || mode == CameraMode.DUAL_VLOG

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 58.dp, start = 12.dp, end = 12.dp, bottom = 16.dp)
            .pixelGlass(
                shape = RoundedCornerShape(28.dp),
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = PixelYellowAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Quick Controls",
                        style = AuraCamTheme.typography.titleLarge,
                        color = PixelTextWhite
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PixelTextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Photo Resolution (Featured prominently)
            SectionHeader(title = "Photo Resolution", icon = Icons.Default.CameraAlt)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PhotoResolution.values()) { res ->
                    PillButton(
                        text = res.label,
                        isSelected = res == photoResolution,
                        onClick = { onPhotoResolutionChange(res) }
                    )
                }
            }

            // Video Resolution (Featured prominently)
            SectionHeader(title = "Video Resolution & FPS", icon = Icons.Default.Videocam)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VideoResolution.values()) { res ->
                    PillButton(
                        text = res.label,
                        isSelected = res == videoResolution,
                        onClick = { onVideoResolutionChange(res) }
                    )
                }
            }

            // Aspect Ratio
            SectionHeader(title = "Aspect Ratio", icon = Icons.Default.AspectRatio)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AspectRatio.values()) { ratio ->
                    PillButton(
                        text = ratio.label,
                        isSelected = ratio == aspectRatio,
                        onClick = { onAspectRatioChange(ratio) }
                    )
                }
            }

            // Capture Format (RAW / JPEG / Ultra HDR)
            SectionHeader(title = "Capture Format", icon = Icons.Default.HighQuality)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CaptureFormat.values()) { format ->
                    PillButton(
                        text = format.label,
                        isSelected = format == captureFormat,
                        onClick = { onCaptureFormatChange(format) }
                    )
                }
            }

            // Flash Mode
            SectionHeader(title = "Flash Mode", icon = Icons.Default.FlashOn)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FlashMode.values()) { flash ->
                    PillButton(
                        text = flash.title,
                        isSelected = flash == flashMode,
                        onClick = { onFlashChange(flash) }
                    )
                }
            }

            // Timer
            SectionHeader(title = "Timer Delay", icon = Icons.Default.Timer)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TimerDuration.values()) { timer ->
                    PillButton(
                        text = timer.label,
                        isSelected = timer == timerDuration,
                        onClick = { onTimerChange(timer) }
                    )
                }
            }

            // Color Profile & Tone LUTs
            SectionHeader(title = "Color Science & Tone LUT", icon = Icons.Default.AutoFixHigh)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ColorProfile.values()) { profile ->
                    PillButton(
                        text = profile.label,
                        isSelected = profile == colorProfile,
                        onClick = { onColorProfileChange(profile) }
                    )
                }
            }

            // Viewfinder Grid
            SectionHeader(title = "Framing Grid", icon = Icons.Default.GridOn)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GridType.values()) { grid ->
                    PillButton(
                        text = grid.label,
                        isSelected = grid == gridType,
                        onClick = { onGridChange(grid) }
                    )
                }
            }

            HorizontalDivider(color = PixelGlassBorderSubtle)

            // Toggles (Ultra HDR, Watermark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ultra HDR Processing",
                        style = AuraCamTheme.typography.bodyMedium,
                        color = PixelTextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Retains 10-bit highlight luminance",
                        style = AuraCamTheme.typography.bodySmall,
                        color = PixelTextMuted
                    )
                }
                Switch(
                    checked = ultraHdr,
                    onCheckedChange = onUltraHdrToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PixelYellowAccent,
                        checkedTrackColor = PixelYellowContainer
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pixel Device Watermark",
                        style = AuraCamTheme.typography.bodyMedium,
                        color = PixelTextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Add EXIF specs badge to photos",
                        style = AuraCamTheme.typography.bodySmall,
                        color = PixelTextMuted
                    )
                }
                Switch(
                    checked = watermarkEnabled,
                    onCheckedChange = onWatermarkToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PixelYellowAccent,
                        checkedTrackColor = PixelYellowContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PixelYellowAccent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title,
            style = AuraCamTheme.cameraTypography.badgeSmall,
            color = PixelTextSecondary,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) PixelYellowAccent else Color(0x33FFFFFF))
            .border(
                width = if (isSelected) 1.5.dp else 0.75.dp,
                color = if (isSelected) PixelYellowAccent else Color(0x22FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) PixelPitchBlack else PixelTextWhite,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}
