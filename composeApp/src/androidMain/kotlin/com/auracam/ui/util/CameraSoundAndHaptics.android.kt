package com.auracam.ui.util

import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSoundAndHaptics(): SoundAndHaptics {
    val context = LocalContext.current
    val instance = remember(context) { AndroidSoundAndHaptics(context) }
    DisposableEffect(instance) { onDispose { instance.release() } }
    return instance
}

private class AndroidSoundAndHaptics(context: Context) : SoundAndHaptics {
    private val appContext = context.applicationContext

    private val sound: MediaActionSound? = runCatching {
        MediaActionSound().apply {
            load(MediaActionSound.SHUTTER_CLICK)
            load(MediaActionSound.START_VIDEO_RECORDING)
            load(MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }.getOrNull()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val canVibrate = vibrator?.hasVibrator() == true

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
        if (!canVibrate) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun vibrateLevelLock() {
        if (!canVibrate) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrator?.vibrate(VibrationEffect.createOneShot(15, 80))
        }
    }

    override fun release() {
        sound?.release()
    }
}
