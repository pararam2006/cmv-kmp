package com.pararam2006.cmv.domain.repository

import com.pararam2006.cmv.domain.model.TrackVolume
import kotlinx.coroutines.flow.Flow

interface TrackVolumeRepository {
    fun getAllTrackVolumes(): Flow<List<TrackVolume>>
    suspend fun getTrackVolume(title: String, artist: String?): TrackVolume?
    suspend fun getTrackVolumeById(id: Int): TrackVolume?
    suspend fun saveTrackVolume(trackVolume: TrackVolume)
    suspend fun deleteTrackVolume(trackVolume: TrackVolume)
    suspend fun deleteTrackVolumeById(id: Int)
}
