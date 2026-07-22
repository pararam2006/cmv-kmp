package com.pararam2006.cmv.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackVolumeDao {
    @Query("SELECT * FROM track_volumes")
    fun getAllTrackVolumes(): Flow<List<TrackVolumeEntity>>

    @Query("SELECT * FROM track_volumes")
    suspend fun getAllTrackVolumesSync(): List<TrackVolumeEntity>

    @Query("SELECT * FROM track_volumes WHERE (trackTitle = :title OR trackTitle LIKE '%' || :title || '%' OR :title LIKE '%' || trackTitle || '%') AND (artistName IS NULL OR :artist IS NULL OR artistName = :artist OR artistName LIKE '%' || :artist || '%' OR :artist LIKE '%' || artistName || '%') LIMIT 1")
    suspend fun getTrackVolume(title: String, artist: String?): TrackVolumeEntity?

    @Query("SELECT * FROM track_volumes WHERE id = :id")
    suspend fun getTrackVolumeById(id: Int): TrackVolumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackVolume(trackVolume: TrackVolumeEntity)

    @Delete
    suspend fun deleteTrackVolume(trackVolume: TrackVolumeEntity)

    @Query("DELETE FROM track_volumes WHERE id = :id")
    suspend fun deleteTrackVolumeById(id: Int)
}
