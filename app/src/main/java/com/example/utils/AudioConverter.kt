package com.example.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object AudioConverter {
    private const val TAG = "AudioConverter"

    /**
     * Extracts the raw AAC audio track from an MP4 video file and muxes it back into an M4A audio file.
     * M4A uses AAC codec and is the standard industry format for high-quality audio files.
     * If the user specifically wants the .mp3 extension, some media players can play it natively,
     * but M4A/AAC is the native container that does not require heavy CPU transcoding or external FFmpeg.
     */
    suspend fun convertVideoToAudio(
        context: Context,
        videoFile: File,
        targetExtension: String = "mp3", // "mp3" or "m4a"
        onProgress: suspend (Float) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            var extractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(videoFile.absolutePath)

                var audioTrackIndex = -1
                var format: MediaFormat? = null
                val trackCount = extractor.trackCount

                Log.d(TAG, "Muxing tracks count: $trackCount")
                for (i in 0 until trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                    Log.d(TAG, "Track $i Mime type: $mime")
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        format = trackFormat
                        break
                    }
                }

                if (audioTrackIndex < 0 || format == null) {
                    return@withContext Result.failure(Exception("Could not find any audio track in this video file."))
                }

                extractor.selectTrack(audioTrackIndex)

                // Determine output audio file
                val nameWithoutExtension = videoFile.nameWithoutExtension
                val downloadsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val audioFileName = "$nameWithoutExtension.$targetExtension"
                val audioFile = File(downloadsDir, audioFileName)
                if (audioFile.exists()) {
                    audioFile.delete()
                }

                // MediaMuxer supports MP4 output format which works elegantly for M4A and audio tracks (AAC)
                muxer = MediaMuxer(audioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val writeTrackIndex = muxer.addTrack(format)
                muxer.start()

                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    1L
                }

                val maxBufferSize = 1024 * 1024
                val buffer = ByteBuffer.allocate(maxBufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

                var bytesMuxed = 0L
                var lastUpdateTime = System.currentTimeMillis()

                onProgress(0.1f)

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        bufferInfo.size = 0
                        break
                    }

                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)
                    bytesMuxed += bufferInfo.size

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime > 200) {
                        val progress = if (durationUs > 0) {
                            (bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0.1f, 0.9f)
                        } else {
                            0.5f
                        }
                        onProgress(progress)
                        lastUpdateTime = currentTime
                    }

                    extractor.advance()
                }

                onProgress(1.0f)
                Result.success(audioFile)
            } catch (e: Exception) {
                Log.e(TAG, "Audio extraction failed", e)
                Result.failure(e)
            } finally {
                try {
                    extractor?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Extractor release failed", e)
                }
                try {
                    muxer?.stop()
                    muxer?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Muxer stop/release failed", e)
                }
            }
        }
    }
}
