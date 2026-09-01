package com.auracam

import com.auracam.camera.domain.BaseCameraEngine
import com.auracam.camera.domain.CameraMode
import com.auracam.camera.domain.ColorProfile
import com.auracam.camera.domain.DualVlogGeometry
import com.auracam.camera.domain.DualVlogLayout
import com.auracam.camera.domain.PipCorner
import com.auracam.processing.ToneFilterMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class DualVlogTestEngine : BaseCameraEngine(
    coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    simulateSensors = false
)

class DualVlogDirectorTest {

    private val engine = DualVlogTestEngine()

    @AfterTest
    fun tearDown() {
        engine.release()
    }

    @Test
    fun defaultDualVlogLayoutIsSplit5050() {
        assertEquals(DualVlogLayout.SPLIT_50_50, engine.dualVlogLayout.value)
        assertFalse(engine.isDualStreamSwapped.value)
    }

    @Test
    fun layoutSwitchingUpdatesEngineState() {
        engine.setDualVlogLayout(DualVlogLayout.PIP_RECT)
        assertEquals(DualVlogLayout.PIP_RECT, engine.dualVlogLayout.value)

        engine.setDualVlogLayout(DualVlogLayout.PIP_CIRCLE)
        assertEquals(DualVlogLayout.PIP_CIRCLE, engine.dualVlogLayout.value)

        engine.setDualVlogLayout(DualVlogLayout.SIDE_BY_SIDE)
        assertEquals(DualVlogLayout.SIDE_BY_SIDE, engine.dualVlogLayout.value)

        engine.setDualVlogLayout(DualVlogLayout.SPLIT_50_50)
        assertEquals(DualVlogLayout.SPLIT_50_50, engine.dualVlogLayout.value)
    }

    @Test
    fun streamSwapTogglesStateCleanly() {
        assertFalse(engine.isDualStreamSwapped.value)

        engine.swapDualStreams()
        assertTrue(engine.isDualStreamSwapped.value)

        engine.swapDualStreams()
        assertFalse(engine.isDualStreamSwapped.value)
    }

    @Test
    fun pipCornerOffsetsAreBoundedAndCalculatedAccurately() {
        val containerWidth = 1080f
        val containerHeight = 2400f
        val pipWidth = 300f
        val pipHeight = 480f

        val marginX = 40f
        val marginTop = 140f
        val marginBottom = 200f

        val tl = DualVlogGeometry.calculateCornerOffset(
            PipCorner.TOP_LEFT, containerWidth, containerHeight, pipWidth, pipHeight,
            marginX, marginTop, marginBottom
        )
        assertEquals(marginX, tl.x)
        assertEquals(marginTop, tl.y)

        val tr = DualVlogGeometry.calculateCornerOffset(
            PipCorner.TOP_RIGHT, containerWidth, containerHeight, pipWidth, pipHeight,
            marginX, marginTop, marginBottom
        )
        assertEquals(containerWidth - pipWidth - marginX, tr.x)
        assertEquals(marginTop, tr.y)

        val bl = DualVlogGeometry.calculateCornerOffset(
            PipCorner.BOTTOM_LEFT, containerWidth, containerHeight, pipWidth, pipHeight,
            marginX, marginTop, marginBottom
        )
        assertEquals(marginX, bl.x)
        assertEquals(containerHeight - pipHeight - marginBottom, bl.y)

        val br = DualVlogGeometry.calculateCornerOffset(
            PipCorner.BOTTOM_RIGHT, containerWidth, containerHeight, pipWidth, pipHeight,
            marginX, marginTop, marginBottom
        )
        assertEquals(containerWidth - pipWidth - marginX, br.x)
        assertEquals(containerHeight - pipHeight - marginBottom, br.y)
    }

    @Test
    fun findNearestCornerSnapsToClosestQuadrant() {
        val containerWidth = 1000f
        val containerHeight = 2000f
        val pipWidth = 200f
        val pipHeight = 300f

        // Near top-left
        val nearTL = DualVlogGeometry.findNearestCorner(
            50f, 150f,
            containerWidth, containerHeight, pipWidth, pipHeight
        )
        assertEquals(PipCorner.TOP_LEFT, nearTL)

        // Near top-right
        val nearTR = DualVlogGeometry.findNearestCorner(
            800f, 150f,
            containerWidth, containerHeight, pipWidth, pipHeight
        )
        assertEquals(PipCorner.TOP_RIGHT, nearTR)

        // Near bottom-left
        val nearBL = DualVlogGeometry.findNearestCorner(
            50f, 1600f,
            containerWidth, containerHeight, pipWidth, pipHeight
        )
        assertEquals(PipCorner.BOTTOM_LEFT, nearBL)

        // Near bottom-right
        val nearBR = DualVlogGeometry.findNearestCorner(
            800f, 1600f,
            containerWidth, containerHeight, pipWidth, pipHeight
        )
        assertEquals(PipCorner.BOTTOM_RIGHT, nearBR)
    }

    @Test
    fun toneFilterMatricesMatchColorGradingSpecifications() {
        // 1. Natural is 4x5 Identity Matrix
        val naturalMatrix = ToneFilterMatrix.colorMatrixFor(ColorProfile.NATURAL)
        assertEquals(20, naturalMatrix.size)
        assertEquals(1f, naturalMatrix[0]) // R weight
        assertEquals(1f, naturalMatrix[6]) // G weight
        assertEquals(1f, naturalMatrix[12]) // B weight
        assertEquals(1f, naturalMatrix[18]) // A weight
        assertEquals(0f, naturalMatrix[4]) // R offset

        // 2. Real Tone preserves skin melanin warmth
        val realToneMatrix = ToneFilterMatrix.colorMatrixFor(ColorProfile.REAL_TONE)
        assertEquals(20, realToneMatrix.size)
        assertTrue(realToneMatrix[0] > 1.0f) // Warm red lift
        assertTrue(realToneMatrix[4] > 0f) // Positive warmth bias

        // 3. Vibrant increases saturation
        val vibrantMatrix = ToneFilterMatrix.colorMatrixFor(ColorProfile.VIBRANT)
        assertEquals(20, vibrantMatrix.size)
        assertTrue(vibrantMatrix[0] > 1.2f) // Boosted red saturation
        assertTrue(vibrantMatrix[6] > 1.2f) // Boosted green saturation

        // 4. Cinematic Warm provides golden hour 35mm grade
        val cinematicMatrix = ToneFilterMatrix.colorMatrixFor(ColorProfile.CINEMATIC_WARM)
        assertEquals(20, cinematicMatrix.size)
        assertTrue(cinematicMatrix[0] > 1.1f) // Amber red boost
        assertTrue(cinematicMatrix[4] >= 10f) // Warm highlights offset

        // 5. Monochrome reduces RGB channels to luminance weights
        val monoMatrix = ToneFilterMatrix.colorMatrixFor(ColorProfile.HIGH_CONTRAST_MONO)
        assertEquals(20, monoMatrix.size)
        assertEquals(monoMatrix[0], monoMatrix[5]) // R and G luminance weights equalized
        assertEquals(monoMatrix[0], monoMatrix[10]) // B luminance weights equalized
    }

    @Test
    fun dualVlogVideoRecordingGeneratesProperMediaAndTransitionsState() = runTest {
        engine.setMode(CameraMode.DUAL_VLOG)
        engine.setDualVlogLayout(DualVlogLayout.SPLIT_50_50)
        engine.setColorProfile(ColorProfile.CINEMATIC_WARM)

        assertFalse(engine.isRecording.value)
        assertEquals(0, engine.recordingDurationSeconds.value)

        // Start Dual Vlog Recording
        engine.toggleVideoRecording()
        assertTrue(engine.isRecording.value)

        // Stop Dual Vlog Recording
        engine.toggleVideoRecording()
        assertFalse(engine.isRecording.value)
        assertEquals(0, engine.recordingDurationSeconds.value)

        val recorded = assertNotNull(engine.recentMedia.value)
        assertEquals(CameraMode.DUAL_VLOG, recorded.mode)
        assertTrue(recorded.fileName.startsWith("VID_"))
        assertTrue(recorded.fileName.endsWith(".mp4"))
        assertEquals("video/mp4", recorded.exif.format)
        assertEquals(1920, recorded.width)
        assertEquals(1080, recorded.height)
        assertTrue(recorded.timestamp > 0L)
        assertTrue(engine.galleryList.value.contains(recorded))
    }

    @Test
    fun cameraModeTransitionsMaintainDualVlogConfiguration() {
        engine.setDualVlogLayout(DualVlogLayout.PIP_RECT)
        engine.swapDualStreams()
        assertTrue(engine.isDualStreamSwapped.value)
        assertEquals(DualVlogLayout.PIP_RECT, engine.dualVlogLayout.value)

        engine.setMode(CameraMode.PHOTO)
        assertEquals(CameraMode.PHOTO, engine.cameraMode.value)

        engine.setMode(CameraMode.DUAL_VLOG)
        assertEquals(CameraMode.DUAL_VLOG, engine.cameraMode.value)
        assertEquals(DualVlogLayout.PIP_RECT, engine.dualVlogLayout.value)
        assertTrue(engine.isDualStreamSwapped.value)
    }
}
