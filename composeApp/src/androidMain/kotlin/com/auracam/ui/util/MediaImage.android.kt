package com.auracam.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val TAG = "AuraCamMediaImage"

private object BitmapCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
}

@Composable
actual fun rememberMediaImage(uri: String, maxDimension: Int): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    return produceState<ImageBitmap?>(initialValue = null, uri, maxDimension) {
        value = withContext(Dispatchers.IO) { decode(context, uri, maxDimension) }
    }.value
}

private fun decode(context: Context, uriString: String, maxDimension: Int): ImageBitmap? {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
    if (uri == Uri.EMPTY) return null

    val cacheKey = "$uriString@$maxDimension"
    BitmapCache.get(cacheKey)?.let { return it.asImageBitmap() }

    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
    if (mimeType.startsWith("video/")) {
        val frame = decodeVideoFrame(context, uri) ?: return null
        BitmapCache.put(cacheKey, frame)
        return frame.asImageBitmap()
    }

    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotated = applyExifRotation(context, uri, bitmap)
        BitmapCache.put(cacheKey, rotated)
        rotated.asImageBitmap()
    } catch (e: Exception) {
        Log.w(TAG, "Unable to decode $uriString", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory decoding $uriString", e)
        null
    }
}

private fun decodeVideoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        Log.w(TAG, "Unable to extract video frame from $uri", e)
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    var largest = max(width, height)
    while (largest / sample > maxDimension) {
        sample *= 2
    }
    return sample
}

private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val degrees = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (e: Exception) {
        0f
    }

    if (degrees == 0f) return bitmap
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}
