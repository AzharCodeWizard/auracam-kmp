package com.auracam.camera.camera2

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

/**
 * Writes captured bytes into `DCIM/AuraCam` via MediaStore.
 *
 * Camera2 hands back raw JPEG bytes rather than saving for us, so this replaces what CameraX's
 * `ImageCapture.OutputFileOptions` used to do.
 */
internal object MediaStoreWriter {

    fun writeImage(
        context: Context,
        displayName: String,
        mimeType: String,
        bytes: ByteArray
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AuraCam")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("No output stream for $uri")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            uri
        } catch (e: Exception) {
            Log.e(CAMERA2_TAG, "Failed writing $displayName", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
