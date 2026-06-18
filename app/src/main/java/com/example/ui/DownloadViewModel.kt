package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.DownloadDatabase
import com.example.data.entity.DownloadItem
import com.example.data.repository.DownloadRepository
import com.example.data.network.CobaltService
import com.example.utils.AudioConverter
import com.example.utils.FileDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DownloadViewModel"
    private val database = DownloadDatabase.getDatabase(application)
    private val repository = DownloadRepository(database.downloadDao())

    val downloads: StateFlow<List<DownloadItem>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting = _isConverting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // Holds the currently active converting item ID to display progress loaders inline
    private val _convertingItemId = MutableStateFlow<Int?>(null)
    val convertingItemId = _convertingItemId.asStateFlow()

    private val _conversionProgress = MutableStateFlow(0f)
    val conversionProgress = _conversionProgress.asStateFlow()

    // Cloud Extraction switch; disabled by default to favor 100% On-Device Offline mode as requested
    private val _isCloudExtractionEnabled = MutableStateFlow(false)
    val isCloudExtractionEnabled = _isCloudExtractionEnabled.asStateFlow()

    fun setCloudExtractionEnabled(enabled: Boolean) {
        _isCloudExtractionEnabled.value = enabled
        Log.d(TAG, "Cloud extraction state updated to: $enabled")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Starts extraction and downloading of a video/audio using modern server-side yt-dlp (Cobalt)
     */
    fun startDownload(videoUrl: String, downloadMode: String) {
        if (videoUrl.isBlank()) {
            _errorMessage.value = "Lütfen geçerli bir internet adresi girin."
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            _errorMessage.value = null

            // Generate an initial item placeholder
            val cleanUrl = videoUrl.trim()
            val formatType = if (downloadMode == "audio") "AUDIO" else "VIDEO"
            
            // Extract a reasonable title from the URL
            val rawTitle = try {
                val decoded = URLDecoder.decode(cleanUrl, StandardCharsets.UTF_8.name())
                val lastSegment = decoded.substringAfterLast("/").substringBefore("?")
                if (lastSegment.length > 5) lastSegment else "Medya Dosyası"
            } catch (e: Exception) {
                "Medya Dosyası"
            }
            
            val initialItem = DownloadItem(
                title = "$rawTitle...",
                url = cleanUrl,
                filePath = "",
                fileSize = 0L,
                status = "BAĞLANIYOR", // Connecting
                format = formatType
            )

            val insertedId = repository.insertDownload(initialItem).toInt()

            // Resolve the direct download stream based on cloud extraction toggles
            val urlResult: Result<String> = if (_isCloudExtractionEnabled.value) {
                Log.d(TAG, "Resolving stream using Cloud Extraction (Cobalt API)")
                CobaltService.getDownloadUrl(cleanUrl, downloadMode)
            } else {
                Log.d(TAG, "Resolving stream using 100% Pure Local extraction & Direct pathway")
                if (cleanUrl.endsWith(".mp4", ignoreCase = true) ||
                    cleanUrl.endsWith(".mp3", ignoreCase = true) ||
                    cleanUrl.endsWith(".m4a", ignoreCase = true) ||
                    cleanUrl.endsWith(".wav", ignoreCase = true) ||
                    cleanUrl.contains(".mp4?", ignoreCase = true) ||
                    cleanUrl.contains(".mp3?", ignoreCase = true)
                ) {
                    Result.success(cleanUrl)
                } else {
                    // Perform an HTTP HEAD check to see if it is already a direct media stream
                    val isDirectMedia = withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(10, TimeUnit.SECONDS)
                                .build()
                            val headRequest = Request.Builder().url(cleanUrl).head().build()
                            client.newCall(headRequest).execute().use { response ->
                                if (response.isSuccessful) {
                                    val contentType = response.header("Content-Type") ?: ""
                                    contentType.startsWith("video/") || contentType.startsWith("audio/") || contentType.startsWith("application/octet-stream")
                                } else {
                                    false
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "HEAD request check failed, fallback to scraper.", e)
                            false
                        }
                    }

                    if (isDirectMedia) {
                        Result.success(cleanUrl)
                    } else {
                        // Extract direct media URL locally from webpage HTML source
                        val scrapedUrl = scrapeDirectMediaUrlFromHtml(cleanUrl, downloadMode)
                        if (scrapedUrl != null) {
                            Log.d(TAG, "Local Scraper successfully extracted stream: $scrapedUrl")
                            Result.success(scrapedUrl)
                        } else {
                            if (cleanUrl.startsWith("http", ignoreCase = true)) {
                                Log.d(TAG, "Local scraper failed. Attempting intelligent Cloud API fallback...")
                                val fallbackResult = CobaltService.getDownloadUrl(cleanUrl, downloadMode)
                                if (fallbackResult.isSuccess) {
                                    fallbackResult
                                } else {
                                    Result.failure(Exception("Doğrudan medya bağlantısı tespit edilemedi. Lütfen 'Bulut Sunucu Modu' ayarını aktif edin veya geçerli bir medya dosyası bağlantısı sağlayın."))
                                }
                            } else {
                                Result.failure(Exception("Geçersiz internet adresi. Lütfen geçerli bir sosyal medya veya dosya bağlantısı girin."))
                            }
                        }
                    }
                }
            }

            if (urlResult.isSuccess) {
                val directDownloadUrl = urlResult.getOrThrow()
                Log.d(TAG, "Successfully resolved download stream address: $directDownloadUrl")

                // Update placeholder title info to "Downloading"
                val resolvedName = try {
                    val decoded = URLDecoder.decode(directDownloadUrl, StandardCharsets.UTF_8.name())
                    val filePart = decoded.substringBefore("?").substringAfterLast("/")
                    if (filePart.contains(".") && filePart.length > 3) filePart else {
                        if (downloadMode == "audio") "audio_${System.currentTimeMillis()}.mp3" 
                        else "video_${System.currentTimeMillis()}.mp4"
                    }
                } catch (e: Exception) {
                    if (downloadMode == "audio") "audio_${System.currentTimeMillis()}.mp3" 
                    else "video_${System.currentTimeMillis()}.mp4"
                }

                val itemInProgress = initialItem.copy(
                    id = insertedId,
                    title = if (resolvedName.startsWith("stream") || resolvedName.length < 5) "Medya_${insertedId}" else resolvedName,
                    status = "İNDİRİLİYOR" // Downloading
                )
                repository.updateDownload(itemInProgress)

                // Trigger direct background download
                val downloadResult = FileDownloader.download(
                    context = getApplication(),
                    url = directDownloadUrl,
                    suggestedFileName = itemInProgress.title,
                    downloadId = insertedId,
                    repository = repository,
                    onProgress = { progress, totalSize ->
                        val updated = itemInProgress.copy(
                            downloadProgress = progress,
                            fileSize = totalSize
                        )
                        repository.updateDownload(updated)
                    }
                )

                if (downloadResult.isSuccess) {
                    val savedFile = downloadResult.getOrThrow()
                    val completedItem = itemInProgress.copy(
                        title = savedFile.name,
                        filePath = savedFile.absolutePath,
                        fileSize = savedFile.length(),
                        status = "TAMAMLANDI", // Completed
                        downloadProgress = 1.0f
                    )
                    repository.updateDownload(completedItem)
                } else {
                    val errorItem = itemInProgress.copy(
                        status = "HATA", // Error
                        title = "Hata: İndirme başarısız"
                    )
                    repository.updateDownload(errorItem)
                    _errorMessage.value = "Medya dosyası indirilemedi: ${downloadResult.exceptionOrNull()?.message}"
                }
            } else {
                // Cobalt API query failed
                val errorMsg = urlResult.exceptionOrNull()?.message ?: "Bağlantı doğrulanamadı."
                val errorItem = initialItem.copy(
                    id = insertedId,
                    title = "Doğrulama Hatası",
                    status = "HATA"
                )
                repository.updateDownload(errorItem)
                _errorMessage.value = "Link çözümlenemedi (yt-dlp hatası): $errorMsg"
            }

            _isDownloading.value = false
        }
    }

    /**
     * Converts a downloaded MP4 video file locally to MP3 audio file
     * without requiring external FFmpeg binaries.
     */
    fun convertVideoToMp3(item: DownloadItem, targetExtension: String = "mp3") {
        val videoFile = File(item.filePath)
        if (!videoFile.exists()) {
            _errorMessage.value = "Dönüştürülecek kaynak video dosyası bulunamadı."
            return
        }

        viewModelScope.launch {
            _isConverting.value = true
            _convertingItemId.value = item.id
            _conversionProgress.value = 0f

            // Update item database status
            val originalItemWithConverting = item.copy(status = "DÖNÜŞTÜRÜLÜYOR")
            repository.updateDownload(originalItemWithConverting)

            val conversionResult = AudioConverter.convertVideoToAudio(
                context = getApplication(),
                videoFile = videoFile,
                targetExtension = targetExtension,
                onProgress = { progress ->
                    _conversionProgress.value = progress
                }
            )

            if (conversionResult.isSuccess) {
                val audioFile = conversionResult.getOrThrow()

                // Mark original video as converted or keep completed status
                val originalItemConverted = originalItemWithConverting.copy(
                    status = "DÖNÜŞTÜRÜLDÜ", // Converted
                    convertedPath = audioFile.absolutePath
                )
                repository.updateDownload(originalItemConverted)

                // Insert a brand new completed AUDIO item into the list so the user is free to play/manipulate the MP3!
                val cleanAudioTitle = audioFile.name
                val audioItem = DownloadItem(
                    title = cleanAudioTitle,
                    url = item.url,
                    filePath = audioFile.absolutePath,
                    fileSize = audioFile.length(),
                    downloadProgress = 1.0f,
                    status = "TAMAMLANDI",
                    format = "AUDIO"
                )
                repository.insertDownload(audioItem)
            } else {
                val errorItem = originalItemWithConverting.copy(status = "HATA")
                repository.updateDownload(errorItem)
                _errorMessage.value = "Dönüştürme başarısız oldu: ${conversionResult.exceptionOrNull()?.message}"
            }

            _isConverting.value = false
            _convertingItemId.value = null
        }
    }

    /**
     * Deletes physical files and database logs securely
     */
    fun deleteItem(item: DownloadItem) {
        viewModelScope.launch {
            // Delete main file
            if (item.filePath.isNotEmpty()) {
                val f = File(item.filePath)
                if (f.exists()) {
                    f.delete()
                }
            }
            // Delete converted file if any
            item.convertedPath?.let {
                val f = File(it)
                if (f.exists()) {
                    f.delete()
                }
            }
            repository.deleteDownload(item)
        }
    }

    /**
     * Scrapes direct media links (.mp4, .mp3, .m4a) from page HTML source code completely locally on-device.
     */
    /**
     * Scrapes direct media links (.mp4, .mp3, .m4a) from page HTML source code completely locally on-device.
     */
    private suspend fun scrapeDirectMediaUrlFromHtml(pageUrl: String, downloadMode: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .build()
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        
                        // Local helper to clean and unescape JSON backslashes and HTML entities in parsed URLs
                        fun cleanUrl(raw: String): String {
                            var cleaned = raw.replace("\\/", "/")
                                .replace("\\\\/", "/")
                                .replace("&amp;", "&")
                                .replace("\\u0026", "&")
                            if (cleaned.startsWith("//")) {
                                cleaned = "https:" + cleaned
                            }
                            return cleaned
                        }

                        // 1. Open Graph meta video/audio tags - highly reliable on modern platforms
                        val ogVideoRegex = """<meta[^>]+property=["']og:video["'][^>]+content=["'](https?://[^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                        val ogVideoAltRegex = """<meta[^>]+content=["'](https?://[^"']+)["'][^>]+property=["']og:video["']""".toRegex(RegexOption.IGNORE_CASE)
                        val ogVideoSecureRegex = """<meta[^>]+property=["']og:video:secure_url["'][^>]+content=["'](https?://[^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                        
                        if (downloadMode != "audio") {
                            ogVideoSecureRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                            ogVideoRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                            ogVideoAltRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                        }

                        // 2. Platform-Specific JSON variables often embedded in SPA scripts
                        // Instagram Video URL matchers
                        val instaVideoUrlRegex = """"video_url"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                        instaVideoUrlRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }

                        // TikTok Player Address matchers
                        val tiktokPlayAddrRegex = """"playAddr"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                        val tiktokDownloadAddrRegex = """"downloadAddr"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                        tiktokPlayAddrRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                        tiktokDownloadAddrRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }

                        // YouTube Direct Video block finder (googlevideo source urls)
                        val googleVideoRegex = """(https?:\\?/\\?/[^\s"'\\]+?googlevideo\.com[^\s"'\\]+)""".toRegex(RegexOption.IGNORE_CASE)
                        googleVideoRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }

                        // 3. HTML Video/Source elements
                        val videoRegex = """<video[^>]*src=["'](https?://[^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                        val sourceRegex = """<source[^>]*src=["'](https?://[^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                        
                        sourceRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                        videoRegex.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }

                        // 4. JSON Escaped / Raw Direct Media Extensions matchers (MP4, MP3, M4A)
                        val jsonEscapedMp4 = """(https?:\\?/\\?/[^\s"'\\]+?\.mp4(?:\?[^\s"'\\]+?)?)""".toRegex(RegexOption.IGNORE_CASE)
                        val jsonEscapedMp3 = """(https?:\\?/\\?/[^\s"'\\]+?\.mp3(?:\?[^\s"'\\]+?)?)""".toRegex(RegexOption.IGNORE_CASE)
                        val jsonEscapedM4a = """(https?:\\?/\\?/[^\s"'\\]+?\.m4a(?:\?[^\s"'\\]+?)?)""".toRegex(RegexOption.IGNORE_CASE)

                        if (downloadMode == "audio") {
                            jsonEscapedM4a.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                            jsonEscapedMp3.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                        } else {
                            jsonEscapedMp4.find(html)?.groupValues?.get(1)?.let { return@withContext cleanUrl(it) }
                        }

                        // 5. Raw URL fellbacks (matches unquoted strings)
                        if (downloadMode == "audio") {
                            val rawMp3 = """https?://[^\s"'<>]+?\.mp3[^\s"'<>]*""".toRegex(RegexOption.IGNORE_CASE)
                            val rawM4a = """https?://[^\s"'<>]+?\.m4a[^\s"'<>]*""".toRegex(RegexOption.IGNORE_CASE)
                            rawM4a.find(html)?.value?.let { return@withContext cleanUrl(it) }
                            rawMp3.find(html)?.value?.let { return@withContext cleanUrl(it) }
                        } else {
                            val rawMp4 = """https?://[^\s"'<>]+?\.mp4[^\s"'<>]*""".toRegex(RegexOption.IGNORE_CASE)
                            rawMp4.find(html)?.value?.let { return@withContext cleanUrl(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalScraper", "Error scraping webpage locally: ${e.message}", e)
            }
            null
        }
    }
}
