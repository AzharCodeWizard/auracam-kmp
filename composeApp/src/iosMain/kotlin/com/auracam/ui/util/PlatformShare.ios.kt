package com.auracam.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.auracam.camera.domain.CapturedMedia
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberPlatformShare(): ((CapturedMedia) -> Unit)? = remember {
    { media ->
        val url = NSURL.URLWithString(media.uri)
        val items = listOfNotNull(url ?: media.fileName)
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.presentViewController(controller, animated = true, completion = null)
    }
}
