package com.auracam

import com.auracam.camera.domain.AspectRatio
import com.auracam.camera.domain.BaseCameraEngine
import com.auracam.camera.domain.CameraMode
import com.auracam.camera.domain.CaptureFormat
import com.auracam.camera.domain.CaptureState
import com.auracam.camera.domain.FlashMode
import com.auracam.camera.domain.LensFacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TestCameraEngine : BaseCameraEngine(
    coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    simulateSensors = false
)

class BaseCameraEngineTest {

    private val engine = TestCameraEngine()

    @AfterTest
    fun tearDown() {
        engine.release()
    }

    @Test
    fun zoomIsClampedToTheSupportedRange() {
        engine.setZoom(-4f)
        assertEquals(0.5f, engine.zoomRatio.value)

        engine.setZoom(99f)
        assertEquals(20.0f, engine.zoomRatio.value)

        engine.setZoom(3.5f)
        assertEquals(3.5f, engine.zoomRatio.value)
    }

    @Test
    fun zoomSelectsTheMatchingPhysicalLens() {
        engine.setZoom(0.5f)
        assertEquals(LensFacing.BACK_ULTRA_WIDE, engine.currentLens.value)

        engine.setZoom(1.0f)
        assertEquals(LensFacing.BACK_WIDE, engine.currentLens.value)

        engine.setZoom(2.0f)
        assertEquals(LensFacing.BACK_TELEPHOTO, engine.currentLens.value)

        engine.setZoom(10.0f)
        assertEquals(LensFacing.BACK_SUPER_TELE, engine.currentLens.value)
    }

    @Test
    fun selectingALensResetsZoomToItsBase() {
        engine.setZoom(7f)
        engine.setLens(LensFacing.BACK_WIDE)
        assertEquals(1.0f, engine.zoomRatio.value)
        assertEquals(LensFacing.BACK_WIDE, engine.currentLens.value)
    }

    @Test
    fun proModeEnablesFocusPeaking() {
        assertFalse(engine.proSettings.value.focusPeakingEnabled)
        engine.setMode(CameraMode.PRO)
        assertTrue(engine.proSettings.value.focusPeakingEnabled)
    }

    @Test
    fun focusPointIsNormalisedAndClearable() {
        engine.setFocusPoint(0.25f, 0.75f)
        val point = assertNotNull(engine.focusPoint.value)
        assertEquals(0.25f, point.x)
        assertEquals(0.75f, point.y)
        assertTrue(point.timestamp > 0L)

        engine.clearFocusPoint()
        assertNull(engine.focusPoint.value)
    }

    @Test
    fun geotaggingIsOffByDefault() {
        assertFalse(engine.geotaggingEnabled.value)
        engine.setGeotaggingEnabled(true)
        assertTrue(engine.geotaggingEnabled.value)
    }

    @Test
    fun capturedPhotoIsPrependedToTheGalleryAndProgressReturnsToIdle() = runTest {
        engine.setMode(CameraMode.PHOTO)
        engine.setCaptureFormat(CaptureFormat.JPEG)

        val first = engine.capturePhoto()
        val second = engine.capturePhoto()

        assertEquals(listOf(second, first), engine.galleryList.value)
        assertEquals(second, engine.recentMedia.value)
        assertEquals(CaptureState.IDLE, engine.captureProgress.value.state)
    }

    @Test
    fun capturedPhotoCarriesModeFormatAndRealTimestamp() = runTest {
        engine.setMode(CameraMode.NIGHT_SIGHT)
        engine.setCaptureFormat(CaptureFormat.RAW_DNG)
        engine.setAspectRatio(AspectRatio.RATIO_1_1)

        val media = engine.capturePhoto()

        assertEquals(CameraMode.NIGHT_SIGHT, media.mode)
        assertEquals(CaptureFormat.RAW_DNG, media.format)
        assertTrue(media.fileName.endsWith(".dng"))
        assertEquals(media.width, media.height)
        assertTrue(media.timestamp > 0L)
        assertTrue(media.exif.timestamp.isNotBlank())
        assertNull(media.exif.location)
    }

    @Test
    fun videoRecordingTogglesAndResetsDuration() = runTest {
        assertFalse(engine.isRecording.value)

        engine.toggleVideoRecording()
        assertTrue(engine.isRecording.value)

        engine.toggleVideoRecording()
        assertFalse(engine.isRecording.value)
        assertEquals(0, engine.recordingDurationSeconds.value)
    }

    @Test
    fun releaseStopsRecordingAndIsIdempotent() = runTest {
        engine.toggleVideoRecording()
        assertTrue(engine.isRecording.value)

        engine.release()
        engine.release()

        assertFalse(engine.isRecording.value)
        assertEquals(0, engine.recordingDurationSeconds.value)
    }

    @Test
    fun quickSettingsAreStoredOnTheEngine() {
        engine.setFlash(FlashMode.TORCH)
        engine.setAspectRatio(AspectRatio.RATIO_16_9)
        engine.toggleUltraHdr(false)
        engine.toggleWatermark(false)

        assertEquals(FlashMode.TORCH, engine.flashMode.value)
        assertEquals(AspectRatio.RATIO_16_9, engine.aspectRatio.value)
        assertFalse(engine.ultraHdrEnabled.value)
        assertFalse(engine.watermarkEnabled.value)
    }
}
