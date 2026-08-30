package com.auracam

import com.auracam.settings.AppSettings
import com.auracam.settings.PersistedSettingsStore
import com.auracam.settings.SettingsPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class InMemoryPersistence(var stored: String? = null) : SettingsPersistence {
    var writeCount = 0

    override fun read(): String? = stored

    override fun write(serialized: String) {
        writeCount++
        stored = serialized
    }
}

class SettingsStoreTest {

    @Test
    fun defaultsAreUsedWhenNothingIsPersisted() {
        val store = PersistedSettingsStore(InMemoryPersistence())
        assertEquals(AppSettings(), store.settings.value)
        assertFalse(store.settings.value.geotaggingEnabled)
    }

    @Test
    fun updatesArePersistedAndRestored() {
        val persistence = InMemoryPersistence()
        PersistedSettingsStore(persistence).update { it.copy(geotaggingEnabled = true, hapticsEnabled = false) }

        val restored = PersistedSettingsStore(persistence)
        assertTrue(restored.settings.value.geotaggingEnabled)
        assertFalse(restored.settings.value.hapticsEnabled)
    }

    @Test
    fun noOpUpdatesDoNotWrite() {
        val persistence = InMemoryPersistence()
        val store = PersistedSettingsStore(persistence)

        store.update { it }
        store.update { it.copy(rawCaptureEnabled = it.rawCaptureEnabled) }

        assertEquals(0, persistence.writeCount)
    }

    @Test
    fun corruptPersistedDataFallsBackToDefaults() {
        val store = PersistedSettingsStore(InMemoryPersistence("{not valid json"))
        assertEquals(AppSettings(), store.settings.value)
    }

    @Test
    fun unknownKeysFromNewerVersionsAreIgnored() {
        val json = """{"geotaggingEnabled":true,"someFutureFlag":42}"""
        val store = PersistedSettingsStore(InMemoryPersistence(json))
        assertTrue(store.settings.value.geotaggingEnabled)
    }
}
