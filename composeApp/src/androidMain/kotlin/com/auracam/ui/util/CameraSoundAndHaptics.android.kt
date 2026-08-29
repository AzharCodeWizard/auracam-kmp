package com.auracam.ui.util

import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual class PlatformSoundAndHaptics : SoundAndHaptics {

    private var sound: MediaActionSound? = null
    private var vibrator: Vibrator? = null

    fun initialize(context: Context) {
        sound = MediaActionSound().apply {
            load(MediaActionSound.SHUTTER_CLICK)
            load(MediaActionSound.START_VIDEO_RECORDING)
            load(MediaActionSound.STOP_VIDEO_RECORDING)
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun playShutterSound() {
        sound?.play(MediaActionSound.SHUTTER_CLICK)
    }

    override fun playVideoStartSound() {
        sound?.play(MediaActionSound.START_VIDEO_RECORDING)
    }

    override fun playVideoStopSound() {
        sound?.play(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    override fun vibrateSnap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(30)
        }
    }

    override fun vibrateLevelLock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(15, 80))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(15)
        }
    }
}
