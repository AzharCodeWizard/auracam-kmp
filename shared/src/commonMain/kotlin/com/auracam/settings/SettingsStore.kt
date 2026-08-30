package com.auracam.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

interface SettingsStore {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}

interface SettingsPersistence {
    fun read(): String?
    fun write(serialized: String)
}

private val settingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class PersistedSettingsStore(private val persistence: SettingsPersistence) : SettingsStore {
    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        runCatching { persistence.write(settingsJson.encodeToString(AppSettings.serializer(), next)) }
    }

    private fun load(): AppSettings = runCatching {
        persistence.read()?.let { settingsJson.decodeFromString(AppSettings.serializer(), it) }
    }.getOrNull() ?: AppSettings()
}
