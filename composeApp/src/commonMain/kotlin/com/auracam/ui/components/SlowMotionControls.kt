package com.auracam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.SlowMotionSpeed
import com.auracam.camera.domain.TimelapseInterval
import com.auracam.ui.theme.*

/**
 * Interactive Speed Selection Capsule for Slow Motion Mode
 */
@Composable
fun SlowMotionSpeedSelector(
    currentSpeed: SlowMotionSpeed,
    onSpeedSelected: (SlowMotionSpeed) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .pixelGlass(
                shape = CircleShape,
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlowMotionSpeed.values().forEach { speed ->
            val isSelected = speed == currentSpeed
            val bgColor = animateColorAsState(if (isSelected) PixelYellowAccent else Color.Transparent)
            val textColor = animateColorAsState(if (isSelected) PixelPitchBlack else PixelTextWhite)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgColor.value)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSpeedSelected(speed) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = speed.shortLabel,
                    color = textColor.value,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Interactive Interval Selection Capsule for Timelapse Mode
 */
@Composable
fun TimelapseIntervalSelector(
    currentInterval: TimelapseInterval,
    onIntervalSelected: (TimelapseInterval) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .pixelGlass(
                shape = CircleShape,
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelapseInterval.values().forEach { interval ->
            val isSelected = interval == currentInterval
            val bgColor = animateColorAsState(if (isSelected) PixelYellowAccent else Color.Transparent)
            val textColor = animateColorAsState(if (isSelected) PixelPitchBlack else PixelTextWhite)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgColor.value)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onIntervalSelected(interval) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = interval.shortLabel,
                    color = textColor.value,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * High-Precision HUD Statistics Bar for Slow Motion Recording
 */
@Composable
fun SlowMotionRecordingHud(
    recordingDurationSeconds: Int,
    speed: SlowMotionSpeed,
    modifier: Modifier = Modifier
) {
    val playbackSeconds = (recordingDurationSeconds / speed.multiplier).toInt()
    val recMins = recordingDurationSeconds / 60
    val recSecs = recordingDurationSeconds % 60
    val playMins = playbackSeconds / 60
    val playSecs = playbackSeconds % 60

    Box(
        modifier = modifier
            .pixelGlass(
                shape = RoundedCornerShape(50),
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(PixelRecordRed)
            )

            Text(
                text = "${recMins.toString().padStart(2, '0')}:${recSecs.toString().padStart(2, '0')}",
                color = PixelTextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "➔",
                color = PixelYellowAccent,
                fontSize = 12.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SlowMotionVideo,
                    contentDescription = null,
                    tint = PixelYellowAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${playMins.toString().padStart(2, '0')}:${playSecs.toString().padStart(2, '0')} (${speed.fps}fps)",
                    color = PixelYellowAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * High-Precision HUD Statistics Bar for Timelapse Recording
 */
@Composable
fun TimelapseRecordingHud(
    recordingDurationSeconds: Int,
    interval: TimelapseInterval,
    modifier: Modifier = Modifier
) {
    val outputSeconds = (recordingDurationSeconds / interval.speedMultiplier).coerceAtLeast(1)
    val recMins = recordingDurationSeconds / 60
    val recSecs = recordingDurationSeconds % 60
    val outMins = outputSeconds / 60
    val outSecs = outputSeconds % 60
    val frames = (recordingDurationSeconds / interval.intervalSeconds).toInt()

    Box(
        modifier = modifier
            .pixelGlass(
                shape = RoundedCornerShape(50),
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(PixelRecordRed)
            )

            Text(
                text = "${recMins.toString().padStart(2, '0')}:${recSecs.toString().padStart(2, '0')}",
                color = PixelTextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "➔",
                color = PixelYellowAccent,
                fontSize = 12.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timelapse,
                    contentDescription = null,
                    tint = PixelYellowAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${outMins.toString().padStart(2, '0')}:${outSecs.toString().padStart(2, '0')} ($frames frames)",
                    color = PixelYellowAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
