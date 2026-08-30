package com.auracam.ui.settings

import androidx.compose.runtime.Composable
import com.auracam.settings.SettingsStore

@Composable
expect fun rememberSettingsStore(): SettingsStore
