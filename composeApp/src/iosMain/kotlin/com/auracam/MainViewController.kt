package com.auracam

import androidx.compose.ui.window.ComposeUIViewController
import com.auracam.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    App()
}
