package com.pararam2006.cmv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_volumes")
data class TrackVolumeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val trackTitle: String,
    val artistName: String?,
    val volumeOffset: Float,
)
