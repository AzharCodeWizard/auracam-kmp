package com.auracam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.*
import com.auracam.ui.theme.*

/**
 * Studio Control Island & Pro Quick Settings Drawer
 */
@Composable
fun QuickSettingsDialog(
    mode: CameraMode,
    currentLens: LensFacing = LensFacing.BACK_WIDE,
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
    val isVideoMode = mode == CameraMode.VIDEO || mode == CameraMode.CINEMATIC || mode == CameraMode.DUAL_VLOG || mode == CameraMode.SLOW_MOTION || mode == CameraMode.TIME_LAPSE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(top = 16.dp, start = 12.dp, end = 12.dp, bottom = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xF212141A))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume clicks */ }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFDB58)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = PixelYellowAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Studio Controls",
                                color = PixelTextWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hardware & Capture Engine",
                                color = PixelTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PixelTextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0x1AFFFFFF))

                // Resolution Selector (Photo or Video based on active mode)
                if (isVideoMode) {
                    StudioSectionHeader(title = "Video Quality & Framerate", icon = Icons.Default.Videocam)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoResolution.values()) { res ->
                            StudioChip(
                                text = res.label,
                                isSelected = res == videoResolution,
                                onClick = { onVideoResolutionChange(res) }
                            )
                        }
                    }
                } else {
                    StudioSectionHeader(title = "Sensor Resolution", icon = Icons.Default.CameraAlt)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PhotoResolution.values()) { res ->
                            StudioChip(
                                text = res.label,
                                isSelected = res == photoResolution,
                                onClick = { onPhotoResolutionChange(res) }
                            )
                        }
                    }
                }

                // Aspect Ratio
                StudioSectionHeader(title = "Framing & Aspect Ratio", icon = Icons.Default.AspectRatio)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AspectRatio.values()) { ratio ->
                        StudioChip(
                            text = ratio.label,
                            isSelected = ratio == aspectRatio,
                            onClick = { onAspectRatioChange(ratio) }
                        )
                    }
                }

                // Flash & Lighting
                val flashTitle = if (currentLens == LensFacing.FRONT) "Selfie Screen Flash & Light Ring" else "Flash & Torch"
                StudioSectionHeader(title = flashTitle, icon = Icons.Default.FlashOn)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FlashMode.values()) { flash ->
                        val label = when {
                            currentLens == LensFacing.FRONT && flash == FlashMode.ON -> "Screen Flash"
                            currentLens == LensFacing.FRONT && flash == FlashMode.TORCH -> "Light Ring"
                            currentLens == LensFacing.FRONT && flash == FlashMode.AUTO -> "Auto Flash"
                            else -> flash.title
                        }
                        StudioChip(
                            text = label,
                            isSelected = flash == flashMode,
                            onClick = { onFlashChange(flash) }
                        )
                    }
                }

                // Capture Format & Ultra HDR
                StudioSectionHeader(title = "Format & Dynamic Range", icon = Icons.Default.HighQuality)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CaptureFormat.values()) { format ->
                        StudioChip(
                            text = format.label,
                            isSelected = format == captureFormat,
                            onClick = { onCaptureFormatChange(format) }
                        )
                    }
                }

                // Color Science / Tone Profiles
                StudioSectionHeader(title = "Film Look & Color Science", icon = Icons.Default.AutoFixHigh)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ColorProfile.values()) { profile ->
                        StudioChip(
                            text = profile.label,
                            isSelected = profile == colorProfile,
                            onClick = { onColorProfileChange(profile) }
                        )
                    }
                }

                // Timer Delay
                StudioSectionHeader(title = "Timer Countdown", icon = Icons.Default.Timer)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TimerDuration.values()) { timer ->
                        StudioChip(
                            text = timer.label,
                            isSelected = timer == timerDuration,
                            onClick = { onTimerChange(timer) }
                        )
                    }
                }

                // Framing Grid
                StudioSectionHeader(title = "Composition Grid", icon = Icons.Default.GridOn)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GridType.values()) { grid ->
                        StudioChip(
                            text = grid.label,
                            isSelected = grid == gridType,
                            onClick = { onGridChange(grid) }
                        )
                    }
                }

                HorizontalDivider(color = Color(0x1AFFFFFF))

                // Dynamic Toggles Row: Ultra HDR + Watermark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ultra HDR Processing",
                            color = PixelTextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "10-bit gainmap computational tone mapping",
                            color = PixelTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = ultraHdr,
                        onCheckedChange = onUltraHdrToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PixelPitchBlack,
                            checkedTrackColor = PixelYellowAccent,
                            uncheckedThumbColor = PixelTextSecondary,
                            uncheckedTrackColor = Color(0x33FFFFFF)
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
                            text = "Camera Metadata Watermark",
                            color = PixelTextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Embed lens, aperture, shutter & ISO info",
                            color = PixelTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = watermarkEnabled,
                        onCheckedChange = onWatermarkToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PixelPitchBlack,
                            checkedTrackColor = PixelYellowAccent,
                            uncheckedThumbColor = PixelTextSecondary,
                            uncheckedTrackColor = Color(0x33FFFFFF)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PixelTextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title.uppercase(),
            color = PixelTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun StudioChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) PixelYellowAccent else Color(0x332A2E38),
        animationSpec = tween(durationMillis = 180)
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) PixelPitchBlack else PixelTextPrimary,
        animationSpec = tween(durationMillis = 180)
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PixelYellowAccent else Color(0x1FFFFFFF),
        animationSpec = tween(durationMillis = 180)
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
