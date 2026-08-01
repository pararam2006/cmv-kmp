package com.pararam2006.cmv.domain.usecase

import com.pararam2006.cmv.domain.repository.TrackVolumeRepository

class GetTrackVolumesUseCase(
    private val repository: TrackVolumeRepository
) {
    operator fun invoke() = repository.getAllTrackVolumes()
}