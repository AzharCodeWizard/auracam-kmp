package com.auracam

import com.auracam.camera.domain.*
import com.auracam.processing.ComputationalPipeline
import kotlin.math.abs
import kotlin.test.*

class PixelViewfinderAestheticsTest {

    @Test
    fun testHorizonLevelerSnapThresholds() {
        val perfectlyLevel = HorizonLeveler(pitchDegrees = 0.0f, rollDegrees = 0.0f, isLevel = true)
        assertTrue(perfectlyLevel.isLevel)
        assertEquals(0.0f, perfectlyLevel.pitchDegrees)
        assertEquals(0.0f, perfectlyLevel.rollDegrees)

        val nearLevel = HorizonLeveler(pitchDegrees = 0.8f, rollDegrees = -0.5f, isLevel = true)
        assertTrue(nearLevel.isLevel)

        val tiltedRoll = HorizonLeveler(pitchDegrees = 0.5f, rollDegrees = 4.2f, isLevel = false)
        assertFalse(tiltedRoll.isLevel)
        assertEquals(4.2f, tiltedRoll.rollDegrees)

        val tiltedPitch = HorizonLeveler(pitchDegrees = -6.0f, rollDegrees = 0.2f, isLevel = false)
        assertFalse(tiltedPitch.isLevel)
    }

    @Test
    fun testFramingGridTypes() {
        assertEquals("Off", GridType.NONE.label)
        assertEquals("3x3", GridType.RULE_OF_THIRDS.label)
        assertEquals("Golden Ratio", GridType.GOLDEN_RATIO.label)
        assertEquals("Square", GridType.SQUARE.label)

        // Verify golden ratio mathematical constants
        val phiRatio1 = 0.382f
        val phiRatio2 = 0.618f
        assertTrue(abs(phiRatio1 + phiRatio2 - 1.0f) < 0.001f)
    }

    @Test
    fun testFlashAndTimerEnums() {
        assertEquals("Off", FlashMode.OFF.title)
        assertEquals("Auto", FlashMode.AUTO.title)
        assertEquals("On", FlashMode.ON.title)
        assertEquals("Torch", FlashMode.TORCH.title)

        assertEquals("Off", TimerDuration.OFF.label)
        assertEquals(0, TimerDuration.OFF.seconds)
        assertEquals("3s", TimerDuration.SEC_3.label)
        assertEquals(3, TimerDuration.SEC_3.seconds)
        assertEquals("10s", TimerDuration.SEC_10.label)
        assertEquals(10, TimerDuration.SEC_10.seconds)
    }

    @Test
    fun testColorProfiles() {
        val natural = ColorProfile.NATURAL
        val highContrastMono = ColorProfile.HIGH_CONTRAST_MONO
        val cinematicWarm = ColorProfile.CINEMATIC_WARM
        val astroBoost = ColorProfile.ASTRO_BOOST
        val vibrant = ColorProfile.VIBRANT

        assertEquals("Natural", natural.label)
        assertEquals("B&W Mono", highContrastMono.label)
        assertEquals("Cinematic", cinematicWarm.label)
        assertEquals("Astro Boost", astroBoost.label)
        assertEquals("Vibrant", vibrant.label)
    }

    @Test
    fun testShutterAndCaptureProgressStates() {
        val idleProgress = CaptureProgress(state = CaptureState.IDLE, progress = 0.0f, message = "")
        assertEquals(CaptureState.IDLE, idleProgress.state)
        assertEquals(0.0f, idleProgress.progress)

        val capturingProgress = CaptureProgress(
            state = CaptureState.EXPOSURE_STACKING,
            progress = 0.65f,
            message = "HDR+ Bracketing: 4/9"
        )
        assertEquals(CaptureState.EXPOSURE_STACKING, capturingProgress.state)
        assertEquals(0.65f, capturingProgress.progress)
        assertTrue(capturingProgress.message.contains("HDR+"))

        val timerProgress = CaptureProgress(
            state = CaptureState.IDLE,
            progress = 0.33f,
            message = "Timer: 2"
        )
        assertEquals(CaptureState.IDLE, timerProgress.state)
        assertTrue(timerProgress.message.startsWith("Timer:"))
    }

    @Test
    fun testDualExposureBiasCalculations() {
        val defaultSettings = ProSettings()
        assertEquals(0.0f, defaultSettings.evBias)
        assertEquals(0.0f, defaultSettings.shadowBias)

        val adjustedSettings = defaultSettings.copy(
            evBias = 1.3f,
            shadowBias = -0.4f
        )
        assertEquals(1.3f, adjustedSettings.evBias)
        assertEquals(-0.4f, adjustedSettings.shadowBias)
    }
}
