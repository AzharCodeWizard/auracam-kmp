package com.auracam.ui.util

import androidx.compose.runtime.Composable

interface SoundAndHaptics {
    fun playShutterSound()
    fun playVideoStartSound()
    fun playVideoStopSound()
    fun vibrateSnap()
    fun vibrateLevelLock()
    fun release()
}

object NoOpSoundAndHaptics : SoundAndHaptics {
    override fun playShutterSound() = Unit
    override fun playVideoStartSound() = Unit
    override fun playVideoStopSound() = Unit
    override fun vibrateSnap() = Unit
    override fun vibrateLevelLock() = Unit
    override fun release() = Unit
}

@Composable
expect fun rememberSoundAndHaptics(): SoundAndHaptics
