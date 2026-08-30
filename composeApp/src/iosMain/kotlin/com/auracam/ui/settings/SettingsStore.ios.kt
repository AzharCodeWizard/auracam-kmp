package com.auracam.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.auracam.settings.PersistedSettingsStore
import com.auracam.settings.SettingsPersistence
import com.auracam.settings.SettingsStore
import platform.Foundation.NSUserDefaults

private const val KEY_SETTINGS = "auracam_settings_json"

@Composable
actual fun rememberSettingsStore(): SettingsStore =
    remember { PersistedSettingsStore(UserDefaultsPersistence()) }

private class UserDefaultsPersistence : SettingsPersistence {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): String? = defaults.stringForKey(KEY_SETTINGS)

    override fun write(serialized: String) {
        defaults.setObject(serialized, KEY_SETTINGS)
    }
}
