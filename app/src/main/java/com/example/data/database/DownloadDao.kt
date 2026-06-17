package com.example.data.database

import androidx.room.*
import com.example.data.entity.DownloadItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_items ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE id = :id")
    suspend fun getDownloadById(id: Int): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadValue(item: DownloadItem): Long

    @Update
    suspend fun updateDownloadValue(item: DownloadItem)

    @Delete
    suspend fun deleteDownloadValue(item: DownloadItem)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteDownloadById(id: Int)
}
