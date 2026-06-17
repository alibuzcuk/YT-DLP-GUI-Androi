package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val filePath: String,
    val fileSize: Long,
    val downloadProgress: Float = 0f,
    val status: String, // "DOWNLOADING", "COMPLETED", "CONVERTING", "CONVERTED", "ERROR"
    val format: String, // "VIDEO" (MP4) or "AUDIO" (MP3/M4A)
    val timestamp: Long = System.currentTimeMillis(),
    val convertedPath: String? = null
)
