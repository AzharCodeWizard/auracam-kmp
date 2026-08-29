package com.auracam.sensor

import com.auracam.camera.domain.HorizonLeveler
import kotlinx.coroutines.flow.StateFlow

interface SensorLeveler {
    val horizonLeveler: StateFlow<HorizonLeveler>
    fun start()
    fun stop()
}

expect class PlatformSensorLeveler() : SensorLeveler
