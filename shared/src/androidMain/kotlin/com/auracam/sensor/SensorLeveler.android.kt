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

actual class PlatformSensorLeveler : SensorLeveler, SensorEventListener {

    private val _horizonLeveler = MutableStateFlow(HorizonLeveler())
    override val horizonLeveler: StateFlow<HorizonLeveler> = _horizonLeveler.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun initialize(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun start() {
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val rollRad = orientationAngles[2]
            val pitchRad = orientationAngles[1]

            val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()
            val pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat()

            val isLevel = abs(rollDeg) < 0.75f
            _horizonLeveler.value = HorizonLeveler(
                rollDegrees = rollDeg,
                pitchDegrees = pitchDeg,
                isLevel = isLevel
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
