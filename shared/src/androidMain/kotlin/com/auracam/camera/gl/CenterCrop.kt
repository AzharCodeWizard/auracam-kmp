package com.auracam.camera.gl

/**
 * Center-crop maths shared by the renderer and the subject tracker.
 *
 * The compositor fills each destination rect by cropping the camera stream rather than
 * letterboxing it, so part of the sensor frame is off-screen. Anything that maps sensor-space
 * coordinates onto the viewport — the tracking reticle in particular — has to apply the exact
 * same crop, which is why this lives in one place instead of being derived twice.
 */
internal object CenterCrop {

    /**
     * Fraction of the source that stays visible along the display's x and y axes.
     *
     * @param rotationDegrees rotation applied to the source before it is fitted
     */
    fun visibleFraction(
        sourceWidth: Int,
        sourceHeight: Int,
        rotationDegrees: Int,
        destWidth: Float,
        destHeight: Float
    ): Pair<Float, Float> {
        val rotated = rotationDegrees == 90 || rotationDegrees == 270
        val srcW = if (rotated) sourceHeight else sourceWidth
        val srcH = if (rotated) sourceWidth else sourceHeight
        if (srcW <= 0 || srcH <= 0 || destWidth <= 0f || destHeight <= 0f) return 1f to 1f
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = destWidth / destHeight
        return if (srcAspect > dstAspect) {
            (dstAspect / srcAspect) to 1f
        } else {
            1f to (srcAspect / dstAspect)
        }
    }

    /**
     * Maps a normalized coordinate in the *displayed* source frame onto the visible window,
     * returning a 0..1 position within the destination rect. Values outside 0..1 fell outside
     * the crop and should not be drawn.
     */
    fun toVisibleWindow(coordinate: Float, visibleFraction: Float): Float {
        if (visibleFraction <= 0f) return coordinate
        val start = 0.5f - visibleFraction / 2f
        return (coordinate - start) / visibleFraction
    }
}
