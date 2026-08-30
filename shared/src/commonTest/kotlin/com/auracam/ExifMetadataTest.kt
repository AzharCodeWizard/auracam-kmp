package com.auracam

import com.auracam.camera.domain.CameraMode
import com.auracam.camera.domain.CaptureFormat
import com.auracam.camera.domain.LensFacing
import com.auracam.camera.domain.ProSettings
import com.auracam.location.GeoLocation
import com.auracam.processing.ComputationalPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExifMetadataTest {

    private fun exif(
        capturedAtEpochMillis: Long = 1_756_000_000_000L,
        location: GeoLocation? = null,
        mode: CameraMode = CameraMode.PHOTO
    ) = ComputationalPipeline.generateExif(
        mode = mode,
        lens = LensFacing.BACK_WIDE,
        zoom = 1.0f,
        proSettings = ProSettings(),
        captureFormat = CaptureFormat.JPEG,
        ultraHdr = false,
        capturedAtEpochMillis = capturedAtEpochMillis,
        location = location
    )

    @Test
    fun locationIsOmittedWhenNoFixIsSupplied() {
        assertNull(exif().location)
    }

    @Test
    fun locationIsFormattedWithHemispheres() {
        val northEast = assertNotNull(
            exif(location = GeoLocation(latitude = 48.8584, longitude = 2.2945)).location
        )
        assertTrue(northEast.contains("N"), northEast)
        assertTrue(northEast.contains("E"), northEast)

        val southWest = assertNotNull(
            exif(location = GeoLocation(latitude = -33.8568, longitude = -151.2153)).location
        )
        assertTrue(southWest.contains("S"), southWest)
        assertTrue(southWest.contains("W"), southWest)
    }

    @Test
    fun timestampReflectsTheCaptureInstantAndIsNotHardcoded() {
        val earlier = exif(capturedAtEpochMillis = 1_700_000_000_000L).timestamp
        val later = exif(capturedAtEpochMillis = 1_800_000_000_000L).timestamp

        assertTrue(earlier.isNotBlank())
        assertTrue(later.isNotBlank())
        assertTrue(earlier != later)
        assertEquals(19, earlier.length, earlier)
    }

    @Test
    fun autoIsoTracksTheSelectedMode() {
        assertEquals(100, exif(mode = CameraMode.PHOTO).iso)
        assertEquals(1600, exif(mode = CameraMode.NIGHT_SIGHT).iso)
        assertEquals(1600, exif(mode = CameraMode.ASTRO).iso)
    }
}
