package com.example.data.repository

import com.example.data.database.DownloadDao
import com.example.data.entity.DownloadItem
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun getDownloadById(id: Int): DownloadItem? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun insertDownload(item: DownloadItem): Long {
        return downloadDao.insertDownloadValue(item)
    }

    suspend fun updateDownload(item: DownloadItem) {
        downloadDao.updateDownloadValue(item)
    }

    suspend fun deleteDownload(item: DownloadItem) {
        downloadDao.deleteDownloadValue(item)
    }

    suspend fun deleteDownloadById(id: Int) {
        downloadDao.deleteDownloadById(id)
    }
}
