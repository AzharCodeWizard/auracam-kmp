package com.auracam.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.HistogramData
import com.auracam.camera.domain.ProSettings
import com.auracam.ui.theme.*

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
            .pixelGlass(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                backgroundColor = PixelGlassScrimHeavy,
                borderColor = PixelGlassBorder
            )
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
                        .background(if (isSelected) PixelYellowAccent else PixelSurfaceContainerHigh)
                        .border(
                            1.dp,
                            if (isSelected) PixelYellowAccent else PixelGlassBorderSubtle,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) PixelPitchBlack else PixelTextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = badgeText,
                            color = if (isSelected) PixelPitchBlack else PixelYellowAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active Control Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center
        ) {
            when (selectedTab) {
                ProTab.ISO -> IsoControl(proSettings, onProSettingsChange)
                ProTab.SHUTTER -> ShutterControl(proSettings, onProSettingsChange)
                ProTab.FOCUS -> FocusControl(proSettings, onProSettingsChange)
                ProTab.EV -> EvControl(proSettings, onProSettingsChange)
                ProTab.WB -> WbControl(proSettings, onProSettingsChange)
                ProTab.HISTOGRAM -> HistogramViewer(histogramData)
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
            label = {
                Text(
                    "Auto ISO",
                    color = if (proSettings.isIsoAuto) PixelPitchBlack else PixelTextWhite,
                    fontWeight = FontWeight.SemiBold
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (proSettings.isIsoAuto) PixelYellowAccent else PixelSurfaceContainerHigh
            ),
            border = BorderStroke(1.dp, if (proSettings.isIsoAuto) PixelYellowAccent else PixelGlassBorderSubtle)
        )

        LazyRow(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(isoValues) { iso ->
                val isSelected = !proSettings.isIsoAuto && proSettings.iso == iso
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PixelYellowAccent else PixelSurfaceContainerHigh)
                        .clickable {
                            onChange { it.copy(iso = iso, isIsoAuto = false) }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$iso",
                        color = if (isSelected) PixelPitchBlack else PixelTextWhite,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutterControl(
    proSettings: ProSettings,
    onChange: ((ProSettings) -> ProSettings) -> Unit
) {
    val speeds = listOf<Long>(8000L, 4000L, 2000L, 1000L, 500L, 250L, 125L, 60L, 30L, 15L, 8L, 4L, 2L, 1L)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AssistChip(
            onClick = {
                onChange { it.copy(isShutterAuto = !it.isShutterAuto) }
            },
            label = {
                Text(
                    "Auto Shutter",
                    color = if (proSettings.isShutterAuto) PixelPitchBlack else PixelTextWhite,
                    fontWeight = FontWeight.SemiBold
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (proSettings.isShutterAuto) PixelYellowAccent else PixelSurfaceContainerHigh
            ),
            border = BorderStroke(1.dp, if (proSettings.isShutterAuto) PixelYellowAccent else PixelGlassBorderSubtle)
        )

        LazyRow(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(speeds) { denom ->
                val isSelected = !proSettings.isShutterAuto && proSettings.shutterSpeedDenominator == denom
                val label = if (denom >= 1L) "1/${denom}s" else "${1 / denom}s"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PixelYellowAccent else PixelSurfaceContainerHigh)
                        .clickable {
                            onChange { it.copy(shutterSpeedDenominator = denom, isShutterAuto = false) }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) PixelPitchBlack else PixelTextWhite,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusControl(
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
                onChange { it.copy(isFocusAuto = !it.isFocusAuto) }
            },
            label = {
                Text(
                    "Auto AF",
                    color = if (proSettings.isFocusAuto) PixelPitchBlack else PixelTextWhite,
                    fontWeight = FontWeight.SemiBold
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (proSettings.isFocusAuto) PixelYellowAccent else PixelSurfaceContainerHigh
            ),
            border = BorderStroke(1.dp, if (proSettings.isFocusAuto) PixelYellowAccent else PixelGlassBorderSubtle)
        )

        Text("🌷", fontSize = 14.sp)

        Slider(
            value = proSettings.manualFocusDistance,
            onValueChange = { dist ->
                onChange { it.copy(manualFocusDistance = dist, isFocusAuto = false) }
            },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = PixelYellowAccent,
                activeTrackColor = PixelYellowAccent,
                inactiveTrackColor = PixelSurfaceContainerHigh
            )
        )

        Text("⛰️", fontSize = 14.sp)
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
        Text("-3.0", style = AuraCamTheme.typography.bodySmall, color = PixelTextMuted)
        Slider(
            value = proSettings.evBias,
            onValueChange = { ev ->
                onChange { it.copy(evBias = kotlin.math.round(ev * 10) / 10f) }
            },
            valueRange = -3.0f..3.0f,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = PixelYellowAccent,
                activeTrackColor = PixelYellowAccent,
                inactiveTrackColor = PixelSurfaceContainerHigh
            )
        )
        Text("+3.0", style = AuraCamTheme.typography.bodySmall, color = PixelTextMuted)
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
            label = {
                Text(
                    "Auto AWB",
                    color = if (proSettings.isWbAuto) PixelPitchBlack else PixelTextWhite,
                    fontWeight = FontWeight.SemiBold
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (proSettings.isWbAuto) PixelYellowAccent else PixelSurfaceContainerHigh
            ),
            border = BorderStroke(1.dp, if (proSettings.isWbAuto) PixelYellowAccent else PixelGlassBorderSubtle)
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
                activeTrackColor = PixelYellowAccent,
                inactiveTrackColor = PixelSurfaceContainerHigh
            )
        )

        Text(
            text = "${proSettings.kelvinWb}K",
            color = PixelTextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )
    }
}

@Composable
fun HistogramViewer(histogramData: HistogramData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PixelDarkSurface)
            .border(1.dp, PixelGlassBorderSubtle, RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val barWidth = width / 32f

            for (i in 0 until 32) {
                val x = i * barWidth

                // Red channel
                val rH = (histogramData.redBins.getOrElse(i) { 0 } / 100f) * height
                drawRect(
                    color = Color(0x88FF5252),
                    topLeft = Offset(x, height - rH),
                    size = Size(barWidth - 1, rH)
                )

                // Green channel
                val gH = (histogramData.greenBins.getOrElse(i) { 0 } / 100f) * height
                drawRect(
                    color = Color(0x8869F0AE),
                    topLeft = Offset(x, height - gH),
                    size = Size(barWidth - 1, gH)
                )

                // Blue channel
                val bH = (histogramData.blueBins.getOrElse(i) { 0 } / 100f) * height
                drawRect(
                    color = Color(0x88448AFF),
                    topLeft = Offset(x, height - bH),
                    size = Size(barWidth - 1, bH)
                )
            }
        }
    }
}
