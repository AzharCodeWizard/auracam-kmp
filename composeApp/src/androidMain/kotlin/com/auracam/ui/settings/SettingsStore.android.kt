package com.auracam.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.auracam.settings.PersistedSettingsStore
import com.auracam.settings.SettingsPersistence
import com.auracam.settings.SettingsStore
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "auracam_settings"
private const val KEY_SETTINGS = "settings_json"

@Composable
actual fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { PersistedSettingsStore(SharedPreferencesPersistence(context)) }
}

private class SharedPreferencesPersistence(context: Context) : SettingsPersistence {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY_SETTINGS, null)

    override fun write(serialized: String) {
        prefs.edit().putString(KEY_SETTINGS, serialized).apply()
    }
}
