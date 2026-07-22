package com.pararam2006.cmv.domain.usecase

import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import kotlinx.coroutines.flow.first

class SaveTrackVolumeUseCase(
    private val repository: TrackVolumeRepository
) {
    suspend operator fun invoke(title: String, artist: String?, offset: Float, id: Int = 0) {
        // Duplicate prevention if it's a new record (id == 0)
        if (id == 0) {
            val existing = repository.getAllTrackVolumes().first().find {
                it.trackTitle.equals(title, ignoreCase = true) &&
                        (it.artistName ?: "").equals(artist ?: "", ignoreCase = true)
            }
            if (existing != null) {
                // Update existing instead of creating duplicate
                repository.saveTrackVolume(existing.copy(volumeOffset = offset))
                return
            }
        }

        repository.saveTrackVolume(
            TrackVolume(
                id = id,
                trackTitle = title,
                artistName = artist,
                volumeOffset = offset,
            )
        )
    }
}
