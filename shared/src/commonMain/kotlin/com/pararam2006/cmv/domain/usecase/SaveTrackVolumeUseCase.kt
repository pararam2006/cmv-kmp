package com.pararam2006.cmv.domain.usecase

import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import kotlinx.coroutines.flow.first

class SaveTrackVolumeUseCase(
    private val repository: TrackVolumeRepository
) {
    suspend operator fun invoke(
        title: String,
        artist: String?,
        offsetDb: Float,
        id: Int = 0,
        offsetModel: VolumeOffsetModel = VolumeOffsetModel.DECIBEL,
    ) {
        if (id == 0) {
            val existing = repository.getAllTrackVolumes().first().find {
                it.trackTitle.equals(title, ignoreCase = true) &&
                        (it.artistName ?: "").equals(artist ?: "", ignoreCase = true)
            }
            if (existing != null) {
                repository.saveTrackVolume(
                    existing.copy(volumeOffsetDb = offsetDb, offsetModel = offsetModel),
                )
                return
            }
        }

        repository.saveTrackVolume(
            TrackVolume(
                id = id,
                trackTitle = title,
                artistName = artist,
                volumeOffsetDb = offsetDb,
                offsetModel = offsetModel,
            )
        )
    }
}