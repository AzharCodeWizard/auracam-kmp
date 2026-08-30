package com.auracam.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private const val SHUTTER_SYSTEM_SOUND_ID = 1108u
private const val VIDEO_START_SYSTEM_SOUND_ID = 1117u
private const val VIDEO_STOP_SYSTEM_SOUND_ID = 1118u

@Composable
actual fun rememberSoundAndHaptics(): SoundAndHaptics = remember { IosSoundAndHaptics() }

private class IosSoundAndHaptics : SoundAndHaptics {
    private val lightFeedback = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val mediumFeedback = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)

    override fun playShutterSound() = AudioServicesPlaySystemSound(SHUTTER_SYSTEM_SOUND_ID)
    override fun playVideoStartSound() = AudioServicesPlaySystemSound(VIDEO_START_SYSTEM_SOUND_ID)
    override fun playVideoStopSound() = AudioServicesPlaySystemSound(VIDEO_STOP_SYSTEM_SOUND_ID)

    override fun vibrateSnap() {
        lightFeedback.prepare()
        lightFeedback.impactOccurred()
    }

    override fun vibrateLevelLock() {
        mediumFeedback.prepare()
        mediumFeedback.impactOccurred()
    }

    override fun release() = Unit
}
