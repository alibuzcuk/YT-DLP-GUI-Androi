package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.entity.DownloadItem

@Database(entities = [DownloadItem::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getDatabase(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "download_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
