package com.auracam.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.auracam.camera.domain.ExposureMask
import com.auracam.ui.theme.PixelFocusPeakingGreen
import com.auracam.ui.theme.PixelRecordRed

@Composable
fun ExposureMaskOverlay(
    mask: ExposureMask,
    modifier: Modifier = Modifier
) {
    if (mask.isEmpty) return

    Canvas(modifier = modifier) {
        val cellWidth = size.width / mask.width
        val cellHeight = size.height / mask.height
        val cellSize = Size(cellWidth, cellHeight)

        if (mask.zebra.isNotEmpty()) {
            for (index in mask.zebra.indices) {
                if (mask.zebra[index].toInt() == 0) continue
                val x = index % mask.width
                val y = index / mask.width
                if ((x + y) % 3 != 0) continue
                drawRect(
                    color = PixelRecordRed.copy(alpha = 0.55f),
                    topLeft = Offset(x * cellWidth, y * cellHeight),
                    size = cellSize
                )
            }
        }

        if (mask.peaking.isNotEmpty()) {
            for (index in mask.peaking.indices) {
                if (mask.peaking[index].toInt() == 0) continue
                val x = index % mask.width
                val y = index / mask.width
                drawRect(
                    color = PixelFocusPeakingGreen.copy(alpha = 0.85f),
                    topLeft = Offset(x * cellWidth, y * cellHeight),
                    size = cellSize
                )
            }
        }
    }
}
