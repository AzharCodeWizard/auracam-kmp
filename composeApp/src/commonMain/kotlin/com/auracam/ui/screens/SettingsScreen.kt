package com.auracam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.ui.theme.PixelYellowAccent

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawCaptureEnabled by remember { mutableStateOf(true) }
    var locationTagging by remember { mutableStateOf(true) }
    var shutterSound by remember { mutableStateOf(false) }
    var framingHints by remember { mutableStateOf(true) }
    var videoStabilization by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Camera Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section: Quality & Capture
            SettingsGroup(title = "Photo & Video") {
                SettingsSwitchItem(
                    icon = Icons.Default.Camera,
                    title = "RAW + JPEG Control",
                    subtitle = "Saves uncompressed DNG files alongside JPEG",
                    checked = rawCaptureEnabled,
                    onCheckedChange = { rawCaptureEnabled = it }
                )
                SettingsSwitchItem(
                    icon = Icons.Default.Videocam,
                    title = "Video Stabilization",
                    subtitle = "OIS + EIS Electronic Steady Cam",
                    checked = videoStabilization,
                    onCheckedChange = { videoStabilization = it }
                )
            }

            // Section: Framing & Assistance
            SettingsGroup(title = "Framing & Assistance") {
                SettingsSwitchItem(
                    icon = Icons.Default.GridOn,
                    title = "Framing Hints",
                    subtitle = "Real-time composition suggestions & level indicator",
                    checked = framingHints,
                    onCheckedChange = { framingHints = it }
                )
            }

            // Section: General
            SettingsGroup(title = "General & Privacy") {
                SettingsSwitchItem(
                    icon = Icons.Default.LocationOn,
                    title = "Save Location",
                    subtitle = "Attach GPS coordinates to EXIF metadata",
                    checked = locationTagging,
                    onCheckedChange = { locationTagging = it }
                )
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Camera Sounds",
                    subtitle = "Play shutter sound on capture",
                    checked = shutterSound,
                    onCheckedChange = { shutterSound = it }
                )
            }

            // Section: About
            SettingsGroup(title = "About AuraCam") {
                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    value = "1.0.0 (Google Play Store Ready)"
                )
                SettingsInfoItem(
                    icon = Icons.Default.Code,
                    title = "Engine",
                    value = "Kotlin Multiplatform + Camera2/X + AVFoundation"
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = PixelYellowAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFB0B0B0),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PixelYellowAccent,
                checkedTrackColor = Color(0x66FFDB58)
            )
        )
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFB0B0B0),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = Color.Gray, fontSize = 11.sp)
        }
    }
}
