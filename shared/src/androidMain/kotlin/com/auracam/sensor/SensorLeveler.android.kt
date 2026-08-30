package com.auracam.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.auracam.camera.domain.HorizonLeveler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private const val LEVEL_TOLERANCE_DEGREES = 0.75f

private const val SMOOTHING = 0.15f

actual class PlatformSensorLeveler : SensorLeveler, SensorEventListener {
    private val _horizonLeveler = MutableStateFlow(HorizonLeveler())
    override val horizonLeveler: StateFlow<HorizonLeveler> = _horizonLeveler.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var listening = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var smoothedRoll = 0f
    private var smoothedPitch = 0f
    private var hasSample = false

    fun initialize(context: Context) {
        val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = manager
        rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometer = if (rotationSensor == null) {
            manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            null
        }
    }

    override fun start() {
        if (listening) return
        val manager = sensorManager ?: return
        val sensor = rotationSensor ?: accelerometer ?: return
        listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                publish(
                    rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat(),
                    pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                )
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                val rollDeg = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble())))
                val pitchDeg = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble())))
                publish(rollDeg = -rollDeg.toFloat(), pitchDeg = (90.0 - pitchDeg).toFloat())
            }
        }
    }

    private fun publish(rollDeg: Float, pitchDeg: Float) {
        if (hasSample) {
            smoothedRoll += (rollDeg - smoothedRoll) * SMOOTHING
            smoothedPitch += (pitchDeg - smoothedPitch) * SMOOTHING
        } else {
            smoothedRoll = rollDeg
            smoothedPitch = pitchDeg
            hasSample = true
        }

        _horizonLeveler.value = HorizonLeveler(
            rollDegrees = smoothedRoll,
            pitchDegrees = smoothedPitch,
            isLevel = abs(smoothedRoll) < LEVEL_TOLERANCE_DEGREES
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
