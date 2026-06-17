package com.example.utils

import android.content.Context
import android.util.Log
import com.example.data.entity.DownloadItem
import com.example.data.repository.DownloadRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object FileDownloader {
    private const val TAG = "FileDownloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        context: Context,
        url: String,
        suggestedFileName: String,
        downloadId: Int,
        repository: DownloadRepository,
        onProgress: suspend (Float, Long) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP error code: ${response.code}"))
                    }

                    val body = response.body ?: return@withContext Result.failure(Exception("Response body is empty"))
                    val contentLength = body.contentLength()

                    // Sanitize file name
                    var sName = suggestedFileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                    if (sName.isBlank()) {
                        sName = "download_${System.currentTimeMillis()}"
                    }

                    // Create downloads directory
                    val downloadsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }

                    val destinationFile = File(downloadsDir, sName)
                    if (destinationFile.exists()) {
                        destinationFile.delete()
                    }

                    var inputStream: InputStream? = null
                    var outputStream: FileOutputStream? = null

                    try {
                        inputStream = body.byteStream()
                        outputStream = FileOutputStream(destinationFile)

                        val data = ByteArray(4096)
                        var totalBytesRead: Long = 0
                        var bytesRead: Int

                        var lastUpdateTime = System.currentTimeMillis()

                        while (inputStream.read(data).also { bytesRead = it } != -1) {
                            outputStream.write(data, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val currentTime = System.currentTimeMillis()
                            // Control database updates throttled to once every 150ms to prevent database locks
                            if (contentLength > 0 && (currentTime - lastUpdateTime > 150)) {
                                val progress = totalBytesRead.toFloat() / contentLength
                                onProgress(progress, contentLength)
                                lastUpdateTime = currentTime
                            }
                        }

                        // Completed download
                        onProgress(1.0f, contentLength)
                        Result.success(destinationFile)
                    } finally {
                        try {
                            inputStream?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing input stream", e)
                        }
                        try {
                            outputStream?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing output stream", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                Result.failure(e)
            }
        }
    }
}
