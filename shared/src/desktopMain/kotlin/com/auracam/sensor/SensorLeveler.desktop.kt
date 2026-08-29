package com.auracam.sensor

import com.auracam.camera.domain.HorizonLeveler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class PlatformSensorLeveler : SensorLeveler {
    private val _horizonLeveler = MutableStateFlow(HorizonLeveler(0f, 0f, true))
    override val horizonLeveler: StateFlow<HorizonLeveler> = _horizonLeveler.asStateFlow()

    override fun start() {}
    override fun stop() {}
}
