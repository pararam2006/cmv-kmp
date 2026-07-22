package com.pararam2006.cmv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackVolumeEntity::class,
        AppInfoEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackVolumeDao(): TrackVolumeDao
    abstract fun appsDao(): AppsDao
    companion object {
        const val DATABASE_NAME = "custom_music_volume_db"
    }
}
