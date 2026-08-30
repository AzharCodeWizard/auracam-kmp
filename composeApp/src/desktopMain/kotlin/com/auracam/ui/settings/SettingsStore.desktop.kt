package com.auracam.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.auracam.settings.PersistedSettingsStore
import com.auracam.settings.SettingsPersistence
import com.auracam.settings.SettingsStore
import java.io.File

@Composable
actual fun rememberSettingsStore(): SettingsStore =
    remember { PersistedSettingsStore(FilePersistence()) }

private class FilePersistence : SettingsPersistence {
    private val file: File = File(
        File(System.getProperty("user.home"), ".auracam").also { it.mkdirs() },
        "settings.json"
    )

    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun write(serialized: String) {
        file.writeText(serialized)
    }
}
