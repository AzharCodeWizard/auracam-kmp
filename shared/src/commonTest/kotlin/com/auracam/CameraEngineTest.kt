package com.auracam

import com.auracam.camera.domain.*
import com.auracam.processing.ComputationalPipeline
import kotlin.test.*

class CameraEngineTest {

    @Test
    fun testCameraModesAndBadges() {
        assertEquals("Night Sight", CameraMode.NIGHT_SIGHT.displayName)
        assertEquals("NIGHT", CameraMode.NIGHT_SIGHT.badgeText)
        assertEquals("Astrophotography", CameraMode.ASTRO.displayName)
        assertEquals("ASTRO", CameraMode.ASTRO.badgeText)
        assertEquals("Pro / Expert", CameraMode.PRO.displayName)
        assertEquals("PRO", CameraMode.PRO.badgeText)
    }

    @Test
    fun testProSettingsFormatting() {
        val autoSettings = ProSettings()
        assertTrue(autoSettings.isIsoAuto)
        assertEquals("Auto", autoSettings.formatIso())
        assertEquals("Auto", autoSettings.formatShutterSpeed())
        assertEquals("Auto", autoSettings.formatWb())
        assertEquals("Auto", autoSettings.formatFocus())

        val manualSettings = ProSettings(
            iso = 800,
            isIsoAuto = false,
            shutterSpeedDenominator = 500,
            isShutterAuto = false,
            kelvinWb = 3200,
            isWbAuto = false,
            manualFocusDistance = 0.95f,
            isFocusAuto = false,
            evBias = 1.3f
        )

        assertEquals("ISO 800", manualSettings.formatIso())
        assertEquals("1/500s", manualSettings.formatShutterSpeed())
        assertEquals("3200K", manualSettings.formatWb())
        assertEquals("Macro 🌷", manualSettings.formatFocus())
    }

    @Test
    fun testComputationalPipelineExifGeneration() {
        val exif = ComputationalPipeline.generateExif(
            mode = CameraMode.PRO,
            lens = LensFacing.BACK_WIDE,
            zoom = 1.0f,
            proSettings = ProSettings(iso = 200, isIsoAuto = false),
            captureFormat = CaptureFormat.RAW_DNG,
            ultraHdr = true
        )

        assertTrue(exif.deviceModel.contains("Pixel"))
        assertEquals("24mm (f/1.68)", exif.lensFocalLength)
        assertEquals(200, exif.iso)
        assertEquals("50 MP (8192 × 6144)", exif.resolution)
        assertTrue(exif.format.contains("Ultra HDR"))

        val watermark = ComputationalPipeline.formatPixelWatermark(exif)
        assertTrue(watermark.startsWith("Shot on Pixel"))
        assertTrue(watermark.contains("24mm"))
        assertTrue(watermark.contains("ISO 200"))
    }

    @Test
    fun testAspectRatioDimensions() {
        assertEquals(4f, AspectRatio.RATIO_4_3.ratioWidth)
        assertEquals(3f, AspectRatio.RATIO_4_3.ratioHeight)
        assertEquals(16f, AspectRatio.RATIO_16_9.ratioWidth)
        assertEquals(9f, AspectRatio.RATIO_16_9.ratioHeight)
        assertEquals(1f, AspectRatio.RATIO_1_1.ratioWidth)
        assertEquals(1f, AspectRatio.RATIO_1_1.ratioHeight)
    }

    @Test
    fun testLensFacingZoomBases() {
        assertEquals(0.5f, LensFacing.BACK_ULTRA_WIDE.zoomBase)
        assertEquals(1.0f, LensFacing.BACK_WIDE.zoomBase)
        assertEquals(2.0f, LensFacing.BACK_TELEPHOTO.zoomBase)
        assertEquals(5.0f, LensFacing.BACK_SUPER_TELE.zoomBase)
    }
}
