package com.auracam.camera.domain

import kotlinx.serialization.Serializable
import kotlin.math.hypot

/**
 * 4 Discrete Magnetic Snap Corners for Picture-in-Picture (PiP) Window.
 */
@Serializable
enum class PipCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Platform-agnostic 2D Coordinate Point for PiP Geometry Calculations.
 */
@Serializable
data class PipOffset(val x: Float, val y: Float)

/**
 * Geometry & Snap Physics Utilities for AuraCam Director Dual Recording.
 */
object DualVlogGeometry {

    /**
     * Calculate absolute pixel offset for a target PiP corner within container bounds.
     */
    fun calculateCornerOffset(
        corner: PipCorner,
        containerWidthPx: Float,
        containerHeightPx: Float,
        pipWidthPx: Float,
        pipHeightPx: Float,
        marginXPx: Float = 40f,
        marginTopPx: Float = 140f,
        marginBottomPx: Float = 200f
    ): PipOffset {
        val leftX = marginXPx
        val rightX = (containerWidthPx - pipWidthPx - marginXPx).coerceAtLeast(leftX)
        val topY = marginTopPx
        val bottomY = (containerHeightPx - pipHeightPx - marginBottomPx).coerceAtLeast(topY)

        return when (corner) {
            PipCorner.TOP_LEFT -> PipOffset(leftX, topY)
            PipCorner.TOP_RIGHT -> PipOffset(rightX, topY)
            PipCorner.BOTTOM_LEFT -> PipOffset(leftX, bottomY)
            PipCorner.BOTTOM_RIGHT -> PipOffset(rightX, bottomY)
        }
    }

    /**
     * Find the nearest magnetic corner based on Euclidean distance.
     */
    fun findNearestCorner(
        currentX: Float,
        currentY: Float,
        containerWidthPx: Float,
        containerHeightPx: Float,
        pipWidthPx: Float,
        pipHeightPx: Float,
        marginXPx: Float = 40f,
        marginTopPx: Float = 140f,
        marginBottomPx: Float = 200f
    ): PipCorner {
        return PipCorner.values().minByOrNull { corner ->
            val target = calculateCornerOffset(
                corner,
                containerWidthPx,
                containerHeightPx,
                pipWidthPx,
                pipHeightPx,
                marginXPx,
                marginTopPx,
                marginBottomPx
            )
            hypot((currentX - target.x).toDouble(), (currentY - target.y).toDouble())
        } ?: PipCorner.TOP_RIGHT
    }
}

/**
 * Destination rectangle in normalized viewport coordinates (0..1, origin top-left).
 *
 * Normalized coordinates are what let the on-screen preview and the recorded MP4 agree:
 * the GL compositor and the Compose chrome both resolve the same rect against their own
 * pixel dimensions, so "record the exact visual layout" holds at any output resolution.
 */
@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun coerceInsideViewport(): NormalizedRect {
        val w = width.coerceIn(0.05f, 1f)
        val h = height.coerceIn(0.05f, 1f)
        val l = left.coerceIn(0f, 1f - w)
        val t = top.coerceIn(0f, 1f - h)
        return NormalizedRect(l, t, l + w, t + h)
    }

    companion object {
        val FULL = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

/**
 * Where each of the two camera streams is drawn for a given layout.
 *
 * [main] carries the stream the user has promoted (full frame in PiP, top half in Split);
 * [inset] carries the other one. Swap is resolved by [DualVlogGeometry.framesFor] so callers
 * never re-derive it.
 */
data class DualVlogFrames(
    val rear: NormalizedRect,
    val front: NormalizedRect,
    /** True when the front stream is the promoted (main) one. */
    val frontIsMain: Boolean
)

/**
 * Default PiP window size as a fraction of the viewport, per layout.
 */
private const val PIP_WIDTH_FRACTION = 0.30f
private const val PIP_HEIGHT_FRACTION = 0.26f

/**
 * Normalized geometry used by the Android GL compositor and the Compose chrome.
 */
object DualVlogNormalizedGeometry {

    /** Default PiP rect for a corner, sized as a fraction of the viewport. */
    fun pipRectFor(
        corner: PipCorner,
        widthFraction: Float = PIP_WIDTH_FRACTION,
        heightFraction: Float = PIP_HEIGHT_FRACTION,
        marginX: Float = 0.035f,
        marginTop: Float = 0.085f,
        marginBottom: Float = 0.085f
    ): NormalizedRect {
        val w = widthFraction.coerceIn(0.05f, 1f)
        val h = heightFraction.coerceIn(0.05f, 1f)
        val left = marginX
        val right = 1f - marginX - w
        val top = marginTop
        val bottom = 1f - marginBottom - h
        val (x, y) = when (corner) {
            PipCorner.TOP_LEFT -> left to top
            PipCorner.TOP_RIGHT -> right to top
            PipCorner.BOTTOM_LEFT -> left to bottom
            PipCorner.BOTTOM_RIGHT -> right to bottom
        }
        return NormalizedRect(x, y, x + w, y + h).coerceInsideViewport()
    }

    /** Nearest magnetic corner for a PiP window currently at [rect]. */
    fun nearestCorner(rect: NormalizedRect): PipCorner {
        val centerX = (rect.left + rect.right) / 2f
        val centerY = (rect.top + rect.bottom) / 2f
        return when {
            centerX < 0.5f && centerY < 0.5f -> PipCorner.TOP_LEFT
            centerX >= 0.5f && centerY < 0.5f -> PipCorner.TOP_RIGHT
            centerX < 0.5f -> PipCorner.BOTTOM_LEFT
            else -> PipCorner.BOTTOM_RIGHT
        }
    }

    /**
     * Resolve where the rear and front streams are drawn.
     *
     * [pipRect] is only consulted for the PiP layouts; Split and Side-by-Side are fully
     * determined by the layout itself.
     */
    fun framesFor(
        layout: DualVlogLayout,
        isSwapped: Boolean,
        pipRect: NormalizedRect = pipRectFor(PipCorner.TOP_RIGHT)
    ): DualVlogFrames {
        val (main, inset) = when (layout) {
            DualVlogLayout.SPLIT_50_50 ->
                NormalizedRect(0f, 0f, 1f, 0.5f) to NormalizedRect(0f, 0.5f, 1f, 1f)

            DualVlogLayout.SIDE_BY_SIDE ->
                NormalizedRect(0f, 0f, 0.5f, 1f) to NormalizedRect(0.5f, 0f, 1f, 1f)

            DualVlogLayout.PIP_RECT, DualVlogLayout.PIP_CIRCLE ->
                NormalizedRect.FULL to pipRect.coerceInsideViewport()
        }
        return if (isSwapped) {
            DualVlogFrames(rear = inset, front = main, frontIsMain = true)
        } else {
            DualVlogFrames(rear = main, front = inset, frontIsMain = false)
        }
    }

    /** True when the inset is drawn on top of the main frame and needs chrome (border, shadow). */
    fun isOverlayLayout(layout: DualVlogLayout): Boolean =
        layout == DualVlogLayout.PIP_RECT || layout == DualVlogLayout.PIP_CIRCLE
}
