package com.auracam.ui.util

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.auracam.camera.domain.CapturedMedia

@Composable
actual fun rememberPlatformShare(): (CapturedMedia) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { media ->
            try {
                val uri = Uri.parse(media.uri)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (media.fileName.endsWith(".mp4")) "video/mp4" else "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Shot with AuraCam: ${media.fileName}")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Photo via"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
