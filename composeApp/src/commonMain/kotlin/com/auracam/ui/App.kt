package com.auracam.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.auracam.camera.domain.PlatformCameraEngine
import com.auracam.ui.screens.SettingsScreen
import com.auracam.ui.screens.ViewfinderScreen
import com.auracam.ui.theme.AuraCamTheme

enum class ScreenState {
    VIEWFINDER,
    SETTINGS
}

@Composable
fun App() {
    val cameraEngine = remember { PlatformCameraEngine() }
    var currentScreen by remember { mutableStateOf(ScreenState.VIEWFINDER) }

    AuraCamTheme {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == ScreenState.SETTINGS) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            }
        ) { screen ->
            when (screen) {
                ScreenState.VIEWFINDER -> {
                    ViewfinderScreen(
                        engine = cameraEngine,
                        onOpenSettings = { currentScreen = ScreenState.SETTINGS },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ScreenState.SETTINGS -> {
                    SettingsScreen(
                        onBack = { currentScreen = ScreenState.VIEWFINDER },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
