package com.auracam.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.CameraMode
import com.auracam.camera.domain.CaptureProgress
import com.auracam.camera.domain.CaptureState
import com.auracam.camera.domain.CapturedMedia
import com.auracam.ui.theme.PixelRecordRed
import com.auracam.ui.theme.PixelYellowAccent

@Composable
fun ShutterRow(
    mode: CameraMode,
    isRecording: Boolean,
    recordingDurationSeconds: Int,
    captureProgress: CaptureProgress,
    recentMedia: CapturedMedia?,
    onShutterClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCapturing = captureProgress.state != CaptureState.IDLE && captureProgress.state != CaptureState.COMPLETE

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Gallery Preview Thumbnail
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2C))
                .border(1.5.dp, Color(0xFF444444), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onGalleryClick() },
            contentAlignment = Alignment.Center
        ) {
            if (recentMedia != null) {
                // Circular thumbnail representation with mode badge
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF3A3A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = recentMedia.mode.badgeText.take(2),
                        color = PixelYellowAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Center: Pixel Shutter Button
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = !isCapturing,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onShutterClick() },
            contentAlignment = Alignment.Center
        ) {
            // Outer white ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.5.dp, Color.White, CircleShape)
            )

            // Inner Core Button
            if (isRecording) {
                // Recording state: Red rounded square with duration counter
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PixelRecordRed)
                )
            } else if (mode == CameraMode.VIDEO || mode == CameraMode.CINEMATIC) {
                // Video Idle: Red circle core
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(PixelRecordRed)
                )
            } else {
                // Photo / Night Sight / Astro / Pro: White circle core
                val coreScale = if (isCapturing) 0.82f else 1.0f
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(coreScale)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            // Spinner during computational capture
            if (isCapturing) {
                CircularProgressIndicator(
                    progress = { captureProgress.progress },
                    modifier = Modifier.size(82.dp),
                    color = PixelYellowAccent,
                    strokeWidth = 4.dp
                )
            }
        }

        // Right: Camera Flip Button
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2C))
                .border(1.5.dp, Color(0xFF444444), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onFlipCamera() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
