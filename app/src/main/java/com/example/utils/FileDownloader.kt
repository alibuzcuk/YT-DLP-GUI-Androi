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

            var totalBytesRead: Long = 0
            var expectedLength: Long = -1
            var attempt = 0
            val maxAttempts = 6
            var lastErrorMessage = ""

            while (attempt < maxAttempts) {
                attempt++
                try {
                    val requestBuilder = Request.Builder()
                        .url(url)

                    // Rotate User-Agent to make requests resilient to strict web crawlers
                    val userAgent = if (attempt % 2 == 0) {
                        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    } else {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }
                    requestBuilder.addHeader("User-Agent", userAgent)
                    requestBuilder.addHeader("Accept", "video/mp4,video/*,audio/*,*/*")
                    requestBuilder.addHeader("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
                    requestBuilder.addHeader("Connection", "keep-alive")

                    // Resume support: add Range header if we already have some bytes downloaded
                    if (totalBytesRead > 0) {
                        requestBuilder.addHeader("Range", "bytes=$totalBytesRead-")
                        Log.d(TAG, "Resuming download from byte $totalBytesRead (attempt $attempt)")
                    } else {
                        Log.d(TAG, "Starting new download (attempt $attempt)")
                    }

                    // Intelligently set Referer on first attempts, omit it on retries to bypass potential hotlink bans
                    if (attempt % 3 != 0) {
                        if (url.contains("tiktok", ignoreCase = true) || url.contains("byteoversea", ignoreCase = true)) {
                            requestBuilder.addHeader("Referer", "https://www.tiktok.com/")
                        } else if (url.contains("instagram", ignoreCase = true) || url.contains("cdninstagram", ignoreCase = true)) {
                            requestBuilder.addHeader("Referer", "https://www.instagram.com/")
                        } else if (url.contains("youtube", ignoreCase = true) || url.contains("googlevideo", ignoreCase = true)) {
                            requestBuilder.addHeader("Referer", "https://www.youtube.com/")
                        } else {
                            requestBuilder.addHeader("Referer", "https://google.com/")
                        }
                    }

                    val request = requestBuilder.build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 206) {
                            // If we tried to resume but server says 416 (Requested Range Not Satisfiable),
                            // maybe the file was already complete or server doesn't support range.
                            if (response.code == 416 && totalBytesRead > 0) {
                                Log.d(TAG, "Range not satisfiable with existing file size, assuming file was fully completed.")
                                return@withContext Result.success(destinationFile)
                            }
                            
                            // 403 Forbidden or 416 during Range-resumption -> Resets back to full download
                            if ((response.code == 403 || response.code == 416) && totalBytesRead > 0) {
                                Log.w(TAG, "Server returned ${response.code} for Range request. Clearing partial data and restarting from 0.")
                                totalBytesRead = 0
                                if (destinationFile.exists()) {
                                    destinationFile.delete()
                                }
                            }
                            throw java.io.IOException("HTTP error code: ${response.code}")
                        }

                        val contentType = response.header("Content-Type") ?: ""
                        if (contentType.contains("text/html", ignoreCase = true) ||
                            contentType.contains("application/json", ignoreCase = true)
                        ) {
                            throw java.io.IOException("Geçersiz medya adresi. Çözümlenen URL bir video/ses dosyası değil, web sayfası veya veri döndürüyor.")
                        }

                        val body = response.body ?: throw java.io.IOException("Response body is empty")
                        
                        // Parse Content-Range or Content-Length to determine true stream size
                        if (expectedLength <= 0) {
                            val contentRange = response.header("Content-Range")
                            if (!contentRange.isNullOrBlank()) {
                                try {
                                    val totalSizeStr = contentRange.substringAfterLast("/")
                                    expectedLength = totalSizeStr.toLong()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse Content-Range: $contentRange", e)
                                }
                            }
                            if (expectedLength <= 0) {
                                expectedLength = body.contentLength()
                            }
                        }

                        val inputStream = body.byteStream()
                        val outputStream = FileOutputStream(destinationFile, totalBytesRead > 0)

                        try {
                            val data = ByteArray(8192)
                            var bytesRead: Int
                            var lastUpdateTime = System.currentTimeMillis()

                            while (inputStream.read(data).also { bytesRead = it } != -1) {
                                outputStream.write(data, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastUpdateTime > 150) {
                                    if (expectedLength > 0) {
                                        val progress = totalBytesRead.toFloat() / expectedLength
                                        onProgress(progress, expectedLength)
                                    } else {
                                        onProgress(-0.5f, totalBytesRead)
                                    }
                                    lastUpdateTime = currentTime
                                }
                            }
                        } finally {
                            try { inputStream.close() } catch (e: Exception) {}
                            try { outputStream.close() } catch (e: Exception) {}
                        }

                        // Check if we fully completed the download
                        if (expectedLength > 0 && totalBytesRead < expectedLength) {
                            throw java.io.IOException("Bağlantı kesildi. Dosya eksik indirildi (İndirilen: $totalBytesRead, Toplam: $expectedLength)")
                        }

                        // Robust check: If we downloaded less than 2 KB, it's almost certainly a tiny error webpage or blank response
                        if (totalBytesRead < 2048) {
                            throw java.io.IOException("Medya dosyası çok küçük veya geçersiz ($totalBytesRead bayt).")
                        }

                        // Completed download
                        onProgress(1.0f, totalBytesRead)
                        return@withContext Result.success(destinationFile)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Download attempt $attempt failed: ${e.message}")
                    lastErrorMessage = e.message ?: "Bilinmeyen bağlantı hatası"
                    if (attempt < maxAttempts) {
                        kotlinx.coroutines.delay(800L * attempt) // Retry with backoff delay
                    }
                }
            }

            // All attempts failed
            Result.failure(Exception("İndirme başarısız. Bağlantı kesildi ve $maxAttempts deneme sonrası kurtarılamadı. Son hata: $lastErrorMessage"))
        }
    }
}
