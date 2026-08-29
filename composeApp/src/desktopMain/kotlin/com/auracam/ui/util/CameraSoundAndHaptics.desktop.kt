package com.auracam.ui.util

actual class PlatformSoundAndHaptics : SoundAndHaptics {
    override fun playShutterSound() {}
    override fun playVideoStartSound() {}
    override fun playVideoStopSound() {}
    override fun vibrateSnap() {}
    override fun vibrateLevelLock() {}
}
