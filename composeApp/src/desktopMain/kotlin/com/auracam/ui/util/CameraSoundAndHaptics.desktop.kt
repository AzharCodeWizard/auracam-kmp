package com.auracam.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit

@Composable
actual fun rememberSoundAndHaptics(): SoundAndHaptics = remember { DesktopSoundAndHaptics }

private object DesktopSoundAndHaptics : SoundAndHaptics {
    override fun playShutterSound() {
        runCatching { Toolkit.getDefaultToolkit().beep() }
    }

    override fun playVideoStartSound() = playShutterSound()
    override fun playVideoStopSound() = playShutterSound()
    override fun vibrateSnap() = Unit
    override fun vibrateLevelLock() = Unit
    override fun release() = Unit
}
