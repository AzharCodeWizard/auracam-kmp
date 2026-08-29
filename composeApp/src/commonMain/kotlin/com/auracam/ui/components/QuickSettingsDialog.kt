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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.*
import com.auracam.ui.theme.*

@Composable
fun QuickSettingsDialog(
    aspectRatio: AspectRatio,
    timerDuration: TimerDuration,
    flashMode: FlashMode,
    captureFormat: CaptureFormat,
    colorProfile: ColorProfile,
    gridType: GridType,
    ultraHdr: Boolean,
    watermarkEnabled: Boolean,
    onAspectRatioChange: (AspectRatio) -> Unit,
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .pixelGlass(
                shape = RoundedCornerShape(28.dp),
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Controls",
                    style = AuraCamTheme.typography.titleLarge,
                    color = PixelTextWhite
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PixelSurfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PixelTextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Aspect Ratio
            SectionLabel("Aspect Ratio")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectRatio.values().forEach { ratio ->
                    PillButton(
                        text = ratio.label,
                        isSelected = ratio == aspectRatio,
                        onClick = { onAspectRatioChange(ratio) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Flash Mode
            SectionLabel("Flash")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlashMode.values().forEach { flash ->
                    PillButton(
                        text = flash.title,
                        isSelected = flash == flashMode,
                        onClick = { onFlashChange(flash) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Timer
            SectionLabel("Timer")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimerDuration.values().forEach { timer ->
                    PillButton(
                        text = timer.label,
                        isSelected = timer == timerDuration,
                        onClick = { onTimerChange(timer) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Format (RAW / JPEG / Ultra HDR)
            SectionLabel("Capture Format")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptureFormat.values().forEach { format ->
                    PillButton(
                        text = format.label,
                        isSelected = format == captureFormat,
                        onClick = { onCaptureFormatChange(format) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Color Profile
            SectionLabel("Pixel Color Science & LUT")
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

            // Grid Type
            SectionLabel("Viewfinder Grid")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridType.values().forEach { grid ->
                    PillButton(
                        text = grid.label,
                        isSelected = grid == gridType,
                        onClick = { onGridChange(grid) },
                        modifier = Modifier.weight(1f)
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
                Column {
                    Text(
                        text = "Ultra HDR Processing",
                        style = AuraCamTheme.typography.bodyLarge,
                        color = PixelTextWhite
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
                Column {
                    Text(
                        text = "Pixel Device Watermark",
                        style = AuraCamTheme.typography.bodyLarge,
                        color = PixelTextWhite
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
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = AuraCamTheme.cameraTypography.badgeSmall,
        color = PixelTextSecondary
    )
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
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PixelYellowAccent else PixelSurfaceContainerHigh)
            .border(
                1.dp,
                if (isSelected) PixelYellowAccent else PixelGlassBorderSubtle,
                RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) PixelPitchBlack else PixelTextWhite,
            style = if (isSelected) AuraCamTheme.cameraTypography.pillLabelActive else AuraCamTheme.cameraTypography.pillLabel
        )
    }
}
