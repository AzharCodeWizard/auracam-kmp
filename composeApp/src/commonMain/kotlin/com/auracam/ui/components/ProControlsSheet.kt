package com.auracam.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.HistogramData
import com.auracam.camera.domain.ProSettings
import com.auracam.ui.theme.PixelFocusPeakingGreen
import com.auracam.ui.theme.PixelYellowAccent

enum class ProTab(val title: String) {
    ISO("ISO"),
    SHUTTER("Shutter"),
    FOCUS("Focus"),
    EV("EV"),
    WB("WB"),
    HISTOGRAM("Histogram")
}

@Composable
fun ProControlsSheet(
    proSettings: ProSettings,
    histogramData: HistogramData,
    onProSettingsChange: ((ProSettings) -> ProSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(ProTab.ISO) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xE6181818))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        // Tab Selector Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(ProTab.values()) { tab ->
                val isSelected = tab == selectedTab
                val badgeText = when (tab) {
                    ProTab.ISO -> proSettings.formatIso()
                    ProTab.SHUTTER -> proSettings.formatShutterSpeed()
                    ProTab.FOCUS -> proSettings.formatFocus()
                    ProTab.EV -> "${if (proSettings.evBias >= 0) "+" else ""}${proSettings.evBias} EV"
                    ProTab.WB -> proSettings.formatWb()
                    ProTab.HISTOGRAM -> "RGB"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) PixelYellowAccent else Color(0xFF2B2B2B))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedTab = tab }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) Color.Black else Color(0xFFE0E0E0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = badgeText,
                            color = if (isSelected) Color.Black else PixelYellowAccent,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Content
        when (selectedTab) {
            ProTab.ISO -> {
                IsoControl(
                    proSettings = proSettings,
                    onChange = onProSettingsChange
                )
            }
            ProTab.SHUTTER -> {
                ShutterControl(
                    proSettings = proSettings,
                    onChange = onProSettingsChange
                )
            }
            ProTab.FOCUS -> {
                FocusControl(
                    proSettings = proSettings,
                    onChange = onProSettingsChange
                )
            }
            ProTab.EV -> {
                EvControl(
                    proSettings = proSettings,
                    onChange = onProSettingsChange
                )
            }
            ProTab.WB -> {
                WbControl(
                    proSettings = proSettings,
                    onChange = onProSettingsChange
                )
            }
            ProTab.HISTOGRAM -> {
                HistogramViewer(histogramData = histogramData)
            }
        }
    }
}

@Composable
private fun IsoControl(
    proSettings: ProSettings,
    onChange: ((ProSettings) -> ProSettings) -> Unit
) {
    val isoValues = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AssistChip(
            onClick = {
                onChange { it.copy(isIsoAuto = !it.isIsoAuto) }
            },
            label = { Text("Auto ISO", color = if (proSettings.isIsoAuto) Color.Black else Color.White) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (proSettings.isIsoAuto) PixelYellowAccent else Color(0xFF333333)
            )
        )

        Slider(
            value = isoValues.indexOf(proSettings.iso).coerceAtLeast(0).toFloat(),
            onValueChange = { index ->
                val iso = isoValues[index.toInt().coerceIn(0, isoValues.lastIndex)]
                onChange { it.copy(iso = iso, isIsoAuto = false) }
            },
            valueRange = 0f..(isoValues.size - 1).toFloat(),
            steps = isoValues.size - 2,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = PixelYellowAccent,
                activeTrackColor = PixelYellowAccent
            )
        )

        Text(
            text = proSettings.formatIso(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.width(68.dp)
        )
    }
}

@Composable
private fun ShutterControl(
    proSettings: ProSettings,
    onChange: ((ProSettings) -> ProSettings) -> Unit
) {
    val denominators = listOf(8000L, 4000L, 2000L, 1000L, 500L, 250L, 125L, 60L, 30L, 15L, 8L, 4L, 2L, 1L)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AssistChip(
            onClick = {
                onChange { it.copy(isShutterAuto = !it.isShutterAuto) }
            },
            label = { Text("Auto Shutter", color = if (proSettings.isShutterAuto) Color.Black else Color.White) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (proSettings.isShutterAuto) PixelYellowAccent else Color(0xFF333333)
            )
        )

        Slider(
            value = denominators.indexOf(proSettings.shutterSpeedDenominator).coerceAtLeast(0).toFloat(),
            onValueChange = { index ->
                val denom = denominators[index.toInt().coerceIn(0, denominators.lastIndex)]
                onChange { it.copy(shutterSpeedDenominator = denom, isShutterAuto = false) }
            },
            valueRange = 0f..(denominators.size - 1).toFloat(),
            steps = denominators.size - 2,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = PixelYellowAccent,
                activeTrackColor = PixelYellowAccent
            )
        )

        Text(
            text = proSettings.formatShutterSpeed(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.width(68.dp)
        )
    }
}

@Composable
private fun FocusControl(
    proSettings: ProSettings,
    onChange: ((ProSettings) -> ProSettings) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AssistChip(
                onClick = {
                    onChange { it.copy(isFocusAuto = !it.isFocusAuto) }
                },
                label = { Text("Auto Focus", color = if (proSettings.isFocusAuto) Color.Black else Color.White) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (proSettings.isFocusAuto) PixelYellowAccent else Color(0xFF333333)
                )
            )

            Slider(
                value = proSettings.manualFocusDistance,
                onValueChange = { dist ->
                    onChange { it.copy(manualFocusDistance = dist, isFocusAuto = false) }
                },
                valueRange = 0.0f..1.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = PixelYellowAccent,
                    activeTrackColor = PixelYellowAccent
                )
            )

            Text(
                text = proSettings.formatFocus(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.width(84.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Focus Peaking Outline", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            Switch(
                checked = proSettings.focusPeakingEnabled,
                onCheckedChange = { enabled ->
                    onChange { it.copy(focusPeakingEnabled = enabled) }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PixelFocusPeakingGreen,
                    checkedTrackColor = Color(0x5500FF66)
                )
            )
        }
    }
}

@Composable
private fun EvControl(
    proSettings: ProSettings,
    onChange: ((ProSettings) -> ProSettings) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("-3.0", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = proSettings.evBias,
            onValueChange = { ev ->
                val rounded = (kotlin.math.round(ev * 10) / 10f)
                onChange { it.copy(evBias = rounded) }
            },
            valueRange = -3.0f..3.0f,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = PixelYellowAccent,
                activeTrackColor = PixelYellowAccent
            )
        )
        Text("+3.0", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun WbControl(
    proSettings: ProSettings,
    onChange: ((ProSettings) -> ProSettings) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AssistChip(
            onClick = {
                onChange { it.copy(isWbAuto = !it.isWbAuto) }
            },
            label = { Text("Auto AWB", color = if (proSettings.isWbAuto) Color.Black else Color.White) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (proSettings.isWbAuto) PixelYellowAccent else Color(0xFF333333)
            )
        )

        Slider(
            value = proSettings.kelvinWb.toFloat(),
            onValueChange = { k ->
                onChange { it.copy(kelvinWb = k.toInt(), isWbAuto = false) }
            },
            valueRange = 2000f..10000f,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = PixelYellowAccent,
                activeTrackColor = PixelYellowAccent
            )
        )

        Text(
            text = "${proSettings.kelvinWb}K",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.width(68.dp)
        )
    }
}

@Composable
fun HistogramViewer(histogramData: HistogramData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val barWidth = width / 32f

            for (i in 0 until 32) {
                val x = i * barWidth
                
                // Red bin
                val rH = (histogramData.redBins.getOrElse(i) { 0 } / 100f) * height
                drawRect(
                    color = Color(0x66FF5252),
                    topLeft = Offset(x, height - rH),
                    size = Size(barWidth - 1, rH)
                )

                // Green bin
                val gH = (histogramData.greenBins.getOrElse(i) { 0 } / 100f) * height
                drawRect(
                    color = Color(0x6669F0AE),
                    topLeft = Offset(x, height - gH),
                    size = Size(barWidth - 1, gH)
                )

                // Blue bin
                val bH = (histogramData.blueBins.getOrElse(i) { 0 } / 100f) * height
                drawRect(
                    color = Color(0x66448AFF),
                    topLeft = Offset(x, height - bH),
                    size = Size(barWidth - 1, bH)
                )
            }
        }
    }
}
