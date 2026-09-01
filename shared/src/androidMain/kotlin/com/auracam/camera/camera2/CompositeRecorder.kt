package com.auracam.camera.camera2

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Encodes whatever the GL compositor draws into a single MP4, with synchronised microphone audio.
 *
 * The encoder is fed by a [Surface], so the composited Split/PiP frame is recorded exactly as it
 * appears on screen — including the tone filter, which is already baked into those pixels.
 */
internal class CompositeRecorder(private val context: Context) {

    data class Result(val uri: Uri, val fileName: String, val width: Int, val height: Int)

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var pendingUri: Uri? = null
    private var fileName: String = ""

    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var expectsAudio = false

    private var width = 0
    private var height = 0

    private val stopping = AtomicBoolean(false)
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private val lock = Object()

    @Volatile
    private var firstPtsUs = -1L

    @Volatile
    var recordedDurationMs: Long = 0L
        private set

    /**
     * Prepares the muxer and encoders and returns the surface the compositor should draw into,
     * or null if the pipeline could not be created.
     */
    fun start(
        outputWidth: Int,
        outputHeight: Int,
        frameRate: Int,
        bitRate: Int,
        displayName: String
    ): Surface? {
        // Encoders require even dimensions; odd sizes fail to configure on many devices.
        width = outputWidth and 1.inv()
        height = outputHeight and 1.inv()
        fileName = displayName
        stopping.set(false)
        firstPtsUs = -1L
        recordedDurationMs = 0L
        videoTrack = -1
        audioTrack = -1
        muxerStarted = false

        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AuraCam")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            pendingUri = uri

            val descriptor = resolver.openFileDescriptor(uri, "rw")
                ?: return null
            muxer = descriptor.use {
                MediaMuxer(it.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val surface = startVideoEncoder(frameRate, bitRate)
            expectsAudio = hasAudioPermission() && startAudioPipeline()
            surface
        } catch (e: Exception) {
            Log.e(CAMERA2_TAG, "Recorder start failed", e)
            abort()
            null
        }
    }

    private fun startVideoEncoder(frameRate: Int, bitRate: Int): Surface {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            .apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()
        videoEncoder = encoder
        videoThread = Thread({ drainVideo(encoder) }, "AuraCamVideoEnc").also { it.start() }
        return surface
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startAudioPipeline(): Boolean {
        return try {
        val minBuffer = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, 1)
            .apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuffer)
            }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        audioEncoder = encoder

        @Suppress("MissingPermission")
        val record = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            encoder.stop()
            encoder.release()
            audioEncoder = null
            return false
        }
        audioRecord = record
        record.startRecording()
        audioThread = Thread({ pumpAudio(record, encoder, minBuffer) }, "AuraCamAudioEnc")
            .also { it.start() }
        true
        } catch (e: Exception) {
            Log.w(CAMERA2_TAG, "Audio pipeline unavailable; recording video only", e)
            false
        }
    }

    private fun drainVideo(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (stopping.get()) break
                    }

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(lock) {
                        videoTrack = muxer?.addTrack(encoder.outputFormat) ?: -1
                        maybeStartMuxer()
                    }

                    index >= 0 -> {
                        val buffer = encoder.getOutputBuffer(index)
                        writeSample(buffer, info, videoTrack, isVideo = true)
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(CAMERA2_TAG, "Video drain ended", e)
        }
    }

    private fun pumpAudio(record: AudioRecord, encoder: MediaCodec, bufferSize: Int) {
        val pcm = ByteArray(bufferSize)
        val info = MediaCodec.BufferInfo()
        var totalSamples = 0L
        try {
            while (!stopping.get()) {
                val read = record.read(pcm, 0, pcm.size)
                if (read > 0) {
                    val inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = encoder.getInputBuffer(inputIndex)
                        input?.clear()
                        input?.put(pcm, 0, read)
                        val ptsUs = totalSamples * 1_000_000L / AUDIO_SAMPLE_RATE
                        encoder.queueInputBuffer(inputIndex, 0, read, ptsUs, 0)
                        totalSamples += read / 2 // 16-bit mono
                    }
                }
                drainAudio(encoder, info, endOfStream = false)
            }
            val inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    totalSamples * 1_000_000L / AUDIO_SAMPLE_RATE,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drainAudio(encoder, info, endOfStream = true)
        } catch (e: Exception) {
            Log.w(CAMERA2_TAG, "Audio pump ended", e)
        }
    }

    private fun drainAudio(encoder: MediaCodec, info: MediaCodec.BufferInfo, endOfStream: Boolean) {
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(lock) {
                    audioTrack = muxer?.addTrack(encoder.outputFormat) ?: -1
                    maybeStartMuxer()
                }

                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    writeSample(buffer, info, audioTrack, isVideo = false)
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }

                else -> return
            }
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER && endOfStream) return
        }
    }

    private fun writeSample(
        buffer: ByteBuffer?,
        info: MediaCodec.BufferInfo,
        track: Int,
        isVideo: Boolean
    ) {
        if (buffer == null) return
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            info.size = 0
            return
        }
        if (info.size <= 0) return
        synchronized(lock) {
            if (!muxerStarted || track < 0) return
            if (isVideo) {
                if (firstPtsUs < 0) firstPtsUs = info.presentationTimeUs
                info.presentationTimeUs =
                    (info.presentationTimeUs - firstPtsUs).coerceAtLeast(0L)
                recordedDurationMs = info.presentationTimeUs / 1000L
            }
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            runCatching { muxer?.writeSampleData(track, buffer, info) }
                .onFailure { Log.w(CAMERA2_TAG, "Muxer rejected sample", it) }
        }
    }

    /** Must be called while holding [lock]. */
    private fun maybeStartMuxer() {
        if (muxerStarted) return
        if (videoTrack < 0) return
        if (expectsAudio && audioTrack < 0) return
        runCatching {
            muxer?.start()
            muxerStarted = true
        }.onFailure { Log.e(CAMERA2_TAG, "Muxer start failed", it) }
    }

    fun stop(): Result? {
        if (!stopping.compareAndSet(false, true)) return null
        runCatching { videoEncoder?.signalEndOfInputStream() }
        runCatching { videoThread?.join(1500) }
        runCatching { audioThread?.join(1500) }

        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { audioEncoder?.stop() }
        runCatching { audioEncoder?.release() }
        audioEncoder = null

        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        videoEncoder = null

        val started = synchronized(lock) { muxerStarted }
        runCatching {
            if (started) muxer?.stop()
            muxer?.release()
        }.onFailure { Log.w(CAMERA2_TAG, "Muxer stop failed", it) }
        muxer = null

        val uri = pendingUri
        pendingUri = null
        videoThread = null
        audioThread = null

        if (uri == null) return null
        if (!started) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return null
        }
        publish(uri)
        return Result(uri, fileName, width, height)
    }

    private fun publish(uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        }
    }

    private fun abort() {
        stopping.set(true)
        runCatching { videoEncoder?.release() }
        runCatching { audioEncoder?.release() }
        runCatching { audioRecord?.release() }
        runCatching { muxer?.release() }
        videoEncoder = null
        audioEncoder = null
        audioRecord = null
        muxer = null
        pendingUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
        pendingUri = null
    }

    private companion object {
        const val AUDIO_SAMPLE_RATE = 44100
        const val AUDIO_BIT_RATE = 128_000
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
