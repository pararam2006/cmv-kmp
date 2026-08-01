package com.pararam2006.cmv.domain.usecase

import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository

class DeleteTrackVolumeUseCase(
    private val repository: TrackVolumeRepository
) {
    suspend operator fun invoke(trackVolume: TrackVolume) {
        repository.deleteTrackVolume(trackVolume)
    }

    suspend operator fun invoke(id: Int) {
        repository.deleteTrackVolumeById(id)
    }
}