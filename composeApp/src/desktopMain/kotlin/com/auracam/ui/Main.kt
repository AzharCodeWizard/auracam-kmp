package com.auracam.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AuraCam - Pixel Pro Camera Studio",
        state = WindowState(width = 440.dp, height = 920.dp)
    ) {
        App()
    }
}
