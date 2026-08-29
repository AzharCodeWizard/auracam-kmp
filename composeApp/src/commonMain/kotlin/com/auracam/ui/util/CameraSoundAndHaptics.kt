package com.auracam.ui.util

interface SoundAndHaptics {
    fun playShutterSound()
    fun playVideoStartSound()
    fun playVideoStopSound()
    fun vibrateSnap()
    fun vibrateLevelLock()
}

expect class PlatformSoundAndHaptics() : SoundAndHaptics
