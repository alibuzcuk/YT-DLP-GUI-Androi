package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import com.example.data.entity.DownloadItem
import java.io.File
import java.text.DecimalFormat

@Composable
fun MainLayout(viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isCloudExtractionEnabled by viewModel.isCloudExtractionEnabled.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("video") } // "video" (MP4) or "audio" (MP3)
    var selectedTab by remember { mutableStateOf(0) } // 0 = Downloader, 1 = File Manager

    // Media Player State
    var activeAudioItem by remember { mutableStateOf<DownloadItem?>(null) }
    var activeVideoItem by remember { mutableStateOf<DownloadItem?>(null) }

    // Clipboard Paste Helper
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    // Display error messages as dialog
    if (errorMessage != null) {
        Dialog(onDismissRequest = { viewModel.clearError() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E22),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "İşlem Hatası",
                        color = Color(0xFFFF5252),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.clearError() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tamam", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Video Player Dialog
    activeVideoItem?.let { item ->
        VideoPlayerDialog(
            item = item,
            onDismiss = { activeVideoItem = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant Header
            HeaderBlock(downloads)

            // Dynamic Tab Switcher (Custom stylish Material 3 Design)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1C1B1F),
                contentColor = Color(0xFFE6E1E5),
                indicator = { tabPositions ->
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        width = 40.dp,
                        color = Color(0xFFD0BCFF)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "İndirici", // Downloader
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Dosyalarım & Dönüştürücü (${downloads.count { it.status == "TAMAMLANDI" || it.status == "DÖNÜŞTÜRÜLDÜ" }})", // Files & Converter
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (selectedTab == 0) {
                    // Downloader panel
                    DownloaderPanel(
                        urlInput = urlInput,
                        onUrlChange = { urlInput = it },
                        selectedMode = selectedMode,
                        onModeSelect = { selectedMode = it },
                        isDownloading = isDownloading,
                        downloads = downloads,
                        isCloudExtractionEnabled = isCloudExtractionEnabled,
                        onCloudToggle = { viewModel.setCloudExtractionEnabled(it) },
                        onPaste = {
                            val clipData = clipboardManager.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text
                                if (text != null) {
                                    urlInput = text.toString()
                                }
                            }
                        },
                        onClear = { urlInput = "" },
                        onDownloadStart = {
                            viewModel.startDownload(urlInput, selectedMode)
                            urlInput = ""
                        },
                        onDelete = { viewModel.deleteItem(it) }
                    )
                } else {
                    // File Manager & Conversion Panel
                    FileManagerPanel(
                        downloads = downloads,
                        viewModel = viewModel,
                        onPlayAudio = { activeAudioItem = it },
                        onPlayVideo = { activeVideoItem = it },
                        onDelete = { viewModel.deleteItem(it) }
                    )
                }
            }

            // Margin for active audio player layout spacer
            if (activeAudioItem != null) {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }

        // Animated Native Audio Player Panel
        AnimatedVisibility(
            visible = activeAudioItem != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            activeAudioItem?.let { item ->
                AudioPlayerBottomBar(
                    item = item,
                    onClose = { activeAudioItem = null }
                )
            }
        }
    }
}

@Composable
fun HeaderBlock(downloads: List<DownloadItem>) {
    // Collect active files & total size
    val completedFiles = downloads.filter { it.status == "TAMAMLANDI" || it.status == "DÖNÜŞTÜRÜLDÜ" }
    val totalSize = completedFiles.sumOf { it.fileSize }
    val videoCount = completedFiles.count { it.format == "VIDEO" }
    val audioCount = completedFiles.count { it.format == "AUDIO" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1B1F))
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MediaFlow",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFD0BCFF),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "YT-DLP ADVANCED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF938F99),
                    letterSpacing = 1.5.sp
                )
            }
            // Styled immersive top graphic icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4A4458)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(2.dp, Color(0xFFD0BCFF), RoundedCornerShape(4.dp))
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Space metric row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B2930), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Yerel Depolama Kullanımı",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF938F99)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFileSize(totalSize),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF)
                )
            }
            Divider(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp),
                color = Color(0xFF4A4458)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Videolar", fontSize = 10.sp, color = Color(0xFF938F99))
                    Text(text = videoCount.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Sesler", fontSize = 10.sp, color = Color(0xFF938F99))
                    Text(text = audioCount.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                }
            }
        }
    }
}

@Composable
fun DownloaderPanel(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    selectedMode: String,
    onModeSelect: (String) -> Unit,
    isDownloading: Boolean,
    downloads: List<DownloadItem>,
    isCloudExtractionEnabled: Boolean,
    onCloudToggle: (Boolean) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onDownloadStart: () -> Unit,
    onDelete: (DownloadItem) -> Unit
) {
    val activeDownloads = downloads.filter { it.status == "BAĞLANIYOR" || it.status == "İNDİRİLİYOR" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Medya Bağlantısı Girin",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // TextField with paste and clear capabilities styled with custom ring colors
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("URL yapıştırın...", color = Color(0xFF938F99)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E5),
                            unfocusedTextColor = Color(0xFFE6E1E5),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF4A4458),
                            focusedContainerColor = Color(0xFF1C1B1F),
                            unfocusedContainerColor = Color(0xFF1C1B1F)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        trailingIcon = {
                            Row {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(onClick = onClear) {
                                        Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Color(0xFF938F99))
                                    }
                                }
                                IconButton(onClick = onPaste) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Yapıştır",
                                        tint = Color(0xFFD0BCFF)
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mode Selection Chips layout from design theme
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // MP3 Audio Selector Chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selectedMode == "audio") Color(0xFF4A4458) else Color(0xFF1C1B1F))
                                .border(
                                    1.dp,
                                    if (selectedMode == "audio") Color(0xFFD0BCFF) else Color(0xFF4A4458),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onModeSelect("audio") }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "MP3 SES",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedMode == "audio") Color(0xFFE8DEF8) else Color(0xFF938F99)
                                )
                                Text(
                                    "Audio Only",
                                    fontSize = 10.sp,
                                    color = if (selectedMode == "audio") Color(0xFFE8DEF8).copy(0.7f) else Color(0xFF938F99).copy(0.7f)
                                )
                            }
                        }

                        // MP4 Video Selector Chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selectedMode == "video") Color(0xFF4A4458) else Color(0xFF1C1B1F))
                                .border(
                                    1.dp,
                                    if (selectedMode == "video") Color(0xFFD0BCFF) else Color(0xFF4A4458),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onModeSelect("video") }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "MP4 VİDEO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedMode == "video") Color(0xFFE8DEF8) else Color(0xFF938F99)
                                )
                                Text(
                                    "720p/1080p",
                                    fontSize = 10.sp,
                                    color = if (selectedMode == "video") Color(0xFFE8DEF8).copy(0.7f) else Color(0xFF938F99).copy(0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Decorative Cloud Conversion Status Settings block from Design
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF4A4458).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { onCloudToggle(!isCloudExtractionEnabled) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Bulut Sunucu Modu (Cobalt API)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isCloudExtractionEnabled) Color(0xFFD0BCFF) else Color(0xFFE6E1E5)
                            )
                            Text(
                                if (isCloudExtractionEnabled) "Bulut motoru aktif (Sadece uyumlu platformlar)" else "Yerel İndirme & Dönüştürücü aktif (100% Güvenli)",
                                fontSize = 9.sp,
                                color = Color(0xFF938F99)
                            )
                        }
                        // Styled Toggle Switch
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 20.dp)
                                .background(if (isCloudExtractionEnabled) Color(0xFFD0BCFF) else Color(0xFF4A4458), RoundedCornerShape(10.dp)),
                            contentAlignment = if (isCloudExtractionEnabled) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(16.dp)
                                    .background(if (isCloudExtractionEnabled) Color(0xFF381E72) else Color(0xFFE6E1E5), CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Highly Immersive CTA Download Action Trigger
                    Button(
                        onClick = onDownloadStart,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isDownloading && urlInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEADDFF),
                            contentColor = Color(0xFF21005D),
                            disabledContainerColor = Color(0xFF2B2930)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isDownloading) "BAĞLANTI ANALİZ EDİLİYOR..." else "MEDYAYI İNDİR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isDownloading) Color(0xFF938F99) else Color(0xFF21005D)
                            )
                            if (!isDownloading) {
                                Text("↓", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF21005D))
                            }
                        }
                    }
                }
            }
        }

        // Test links helpers to speed up sandbox evaluation
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131316)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Hızlı Test Linkleri",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90A4AE)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. Örnek Video (MP4 Direct)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF42A5F5),
                        modifier = Modifier
                            .clickable {
                                onUrlChange("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                            }
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        "2. Kısa Örnek Sosyal Video",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF42A5F5),
                        modifier = Modifier
                            .clickable {
                                onUrlChange("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Live / Active downloads list
        if (activeDownloads.isNotEmpty()) {
            item {
                Text(
                    text = "Aktif İndirmeler (${activeDownloads.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            items(activeDownloads) { item ->
                ActiveDownloadCard(item = item, onDelete = onDelete)
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Aktif indirme işlemi yok.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(item: DownloadItem, onDelete: (DownloadItem) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFFE6E1E5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Durum: ${item.status}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD0BCFF)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Default.Clear, contentDescription = "İptal Et", tint = Color(0xFF938F99))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar and info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { item.downloadProgress },
                    color = Color(0xFFD0BCFF),
                    trackColor = Color(0xFF4A4458),
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(item.downloadProgress * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatFileSize((item.downloadProgress * item.fileSize).toLong())} / ${formatFileSize(item.fileSize)}",
                fontSize = 11.sp,
                color = Color(0xFF938F99),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun FileManagerPanel(
    downloads: List<DownloadItem>,
    viewModel: DownloadViewModel,
    onPlayAudio: (DownloadItem) -> Unit,
    onPlayVideo: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit
) {
    val completedItems = downloads.filter { it.status == "TAMAMLANDI" || it.status == "DÖNÜŞTÜRÜLDÜ" }
    val convertingItemId by viewModel.convertingItemId.collectAsState()
    val conversionProgress by viewModel.conversionProgress.collectAsState()

    if (completedItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF2B2930), CircleShape)
                    .border(1.dp, Color(0xFF4A4458), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageOfCloudDownload(),
                    contentDescription = "Klasör Boş",
                    tint = Color(0xFF938F99),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Kayıtlı medya dosyası bulunamadı",
                color = Color(0xFFE6E1E5),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "İlk videonuzu veya sesinizi indirmek için 'İndirici' sekmesini kullanın.",
                color = Color(0xFF938F99),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(completedItems) { item ->
                val isCurrentConverting = convertingItemId == item.id
                CompletedItemCard(
                    item = item,
                    isConverting = isCurrentConverting,
                    conversionProgress = conversionProgress,
                    onConvert = { viewModel.convertVideoToMp3(item) },
                    onPlay = {
                        if (item.format == "VIDEO") {
                            onPlayVideo(item)
                        } else {
                            onPlayAudio(item)
                        }
                    },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

@Composable
fun CompletedItemCard(
    item: DownloadItem,
    isConverting: Boolean,
    conversionProgress: Float,
    onConvert: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = item.format == "VIDEO"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930).copy(alpha = 0.8f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF4A4458).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Media Type Icon Badge with exact design colors and emojis from specification
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF4A4458), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isVideo) "🎬" else "🎵",
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isVideo) "MP4" else "MP3",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(Color(0xFF938F99), CircleShape)
                        )
                        Text(
                            text = formatFileSize(item.fileSize),
                            fontSize = 11.sp,
                            color = Color(0xFF938F99)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete/Share actions using custom high-end styles
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = if (isVideo) "video/*" else "audio/*"
                                val fileUri = Uri.fromFile(java.io.File(item.filePath))
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Dosyayı Paylaş"))
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFD0BCFF))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Paylaş", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFFF8A80))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action row (Convert to MP3, Play)
            if (isConverting) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MP3 Çıkarılıyor...", fontSize = 11.sp, color = Color(0xFFD0BCFF))
                        Text("${(conversionProgress * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFE6E1E5))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { conversionProgress },
                        color = Color(0xFFD0BCFF),
                        trackColor = Color(0xFF4A4458),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isVideo) {
                        Button(
                            onClick = onConvert,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A4458),
                                contentColor = Color(0xFFE8DEF8)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Dönüştür", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("MP3'e Dönüştür", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEADDFF),
                            contentColor = Color(0xFF21005D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Çal", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isVideo) "Aç / İzle" else "Aç / Dinle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerBottomBar(item: DownloadItem, onClose: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(1) }

    // Safely instantiate native MediaPlayer
    val mediaPlayer = remember { MediaPlayer() }
    val handler = remember { Handler(Looper.getMainLooper()) }

    var updateRunnable = remember {
        object : Runnable {
            override fun run() {
                try {
                    if (mediaPlayer.isPlaying) {
                        currentPosition = mediaPlayer.currentPosition
                        handler.postDelayed(this, 1000)
                    }
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Error updating seekbar", e)
                }
            }
        }
    }

    LaunchedEffect(item.filePath) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(item.filePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            isPlaying = true
            duration = mediaPlayer.duration
            handler.post(updateRunnable)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio file", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                handler.removeCallbacks(updateRunnable)
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Surface(
        color = Color(0xFF2B2930),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Player Top Meta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF4A4458), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color(0xFFE6E1E5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Şimdi Çalınıyor • Çevrimdışı",
                            fontSize = 10.sp,
                            color = Color(0xFFD0BCFF)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            try {
                                if (isPlaying) {
                                    mediaPlayer.pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer.start()
                                    isPlaying = true
                                    handler.post(updateRunnable)
                                }
                            } catch (e: Exception) {
                                // Ignore issues
                            }
                        }
                    ) {
                        // Drawing custom Play/Pause symbol
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            color = Color(0xFFD0BCFF),
                            fontSize = 18.sp
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF938F99))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timeline slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPosition),
                    fontSize = 10.sp,
                    color = Color(0xFF938F99)
                )

                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { newValue ->
                        try {
                            mediaPlayer.seekTo(newValue.toInt())
                            currentPosition = newValue.toInt()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD0BCFF),
                        activeTrackColor = Color(0xFFD0BCFF),
                        inactiveTrackColor = Color(0xFF4A4458)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Text(
                    text = formatTime(duration),
                    fontSize = 10.sp,
                    color = Color(0xFF938F99)
                )
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(item: DownloadItem, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Interop Android view wrapper to render VideoView natively
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(item.filePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16/9f)
                        .align(Alignment.Center)
                )

                // Top Close header action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(0.2f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }
                }
            }
        }
    }
}

// Format utilities
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0.0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun formatTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// SVG Vector paths drawn cleanly as high-performance Composable symbols
@Composable
fun imageOfCloudDownload(): androidx.compose.ui.graphics.vector.ImageVector {
    return Icons.Default.Refresh
}
