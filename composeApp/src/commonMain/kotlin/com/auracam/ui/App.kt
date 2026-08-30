package com.auracam.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.auracam.camera.domain.PlatformCameraEngine
import com.auracam.ui.screens.CameraPermissionScreen
import com.auracam.ui.screens.SettingsScreen
import com.auracam.ui.screens.ViewfinderScreen
import com.auracam.ui.settings.rememberSettingsStore
import com.auracam.ui.theme.AuraCamTheme
import com.auracam.ui.util.isCameraPermanentlyDenied
import com.auracam.ui.util.rememberCameraPermissionState

enum class ScreenState {
    VIEWFINDER,
    SETTINGS
}

@Composable
fun App() {
    val cameraEngine = remember { PlatformCameraEngine() }
    val permissionState = rememberCameraPermissionState()
    val settingsStore = rememberSettingsStore()
    val settings by settingsStore.settings.collectAsState()

    var currentScreen by remember { mutableStateOf(ScreenState.VIEWFINDER) }

    DisposableEffect(cameraEngine) {
        onDispose { cameraEngine.release() }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.cameraGranted) permissionState.request()
    }

    LaunchedEffect(
        settings.geotaggingEnabled,
        settings.watermarkEnabled,
        settings.videoStabilizationEnabled
    ) {
        cameraEngine.setGeotaggingEnabled(settings.geotaggingEnabled)
        cameraEngine.toggleWatermark(settings.watermarkEnabled)
        cameraEngine.setVideoStabilizationEnabled(settings.videoStabilizationEnabled)
    }

    AuraCamTheme {
        if (!permissionState.cameraGranted) {
            CameraPermissionScreen(
                permanentlyDenied = permissionState.isCameraPermanentlyDenied,
                onRequestPermission = permissionState::request,
                onOpenSettings = permissionState::openAppSettings,
                modifier = Modifier.fillMaxSize()
            )
            return@AuraCamTheme
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == ScreenState.SETTINGS) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            }
        ) { screen ->
            when (screen) {
                ScreenState.VIEWFINDER -> ViewfinderScreen(
                    engine = cameraEngine,
                    settings = settings,
                    microphoneGranted = permissionState.microphoneGranted,
                    onOpenSettings = { currentScreen = ScreenState.SETTINGS },
                    modifier = Modifier.fillMaxSize()
                )

                ScreenState.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onSettingsChange = settingsStore::update,
                    onBack = { currentScreen = ScreenState.VIEWFINDER },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
