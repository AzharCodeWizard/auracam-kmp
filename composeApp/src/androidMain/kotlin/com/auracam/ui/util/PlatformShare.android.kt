package com.auracam.ui.util

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.auracam.camera.domain.CaptureFormat
import com.auracam.camera.domain.CapturedMedia

private const val TAG = "AuraCamShare"

@Composable
actual fun rememberPlatformShare(): ((CapturedMedia) -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        { media ->
            val uri = media.uri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            if (uri == null || uri == Uri.EMPTY) {
                Toast.makeText(context, "Nothing to share yet", Toast.LENGTH_SHORT).show()
            } else {
                val mimeType = mimeTypeFor(media)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, media.fileName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share with")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to launch share sheet", e)
                    Toast.makeText(context, "No app available to share this file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun mimeTypeFor(media: CapturedMedia): String = when {
    media.fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
    media.format == CaptureFormat.RAW_DNG -> "image/x-adobe-dng"
    else -> "image/jpeg"
}
