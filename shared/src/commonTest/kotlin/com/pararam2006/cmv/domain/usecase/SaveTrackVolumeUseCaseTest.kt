package com.pararam2006.cmv.domain.usecase

import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveTrackVolumeUseCaseTest {
    @Test
    fun editingRenamedTrackPreservesItsId() = runTest {
        val repository = FakeRepository(
            TrackVolume(id = 7, trackTitle = "Old", artistName = "Artist", volumeOffset = 1f),
        )

        SaveTrackVolumeUseCase(repository)(
            id = 7,
            title = "Renamed",
            artist = "Artist",
            offset = 1.2f,
        )

        assertEquals(1, repository.tracks.value.size)
        assertEquals(7, repository.tracks.value.single().id)
        assertEquals("Renamed", repository.tracks.value.single().trackTitle)
    }

    private class FakeRepository(initial: TrackVolume) : TrackVolumeRepository {
        val tracks = MutableStateFlow(listOf(initial))

        override fun getAllTrackVolumes(): Flow<List<TrackVolume>> = tracks
        override suspend fun getTrackVolume(title: String, artist: String?): TrackVolume? = null
        override suspend fun getTrackVolumeById(id: Int): TrackVolume? =
            tracks.value.firstOrNull { it.id == id }

        override suspend fun saveTrackVolume(trackVolume: TrackVolume) {
            tracks.value = tracks.value.filterNot { it.id == trackVolume.id } + trackVolume
        }

        override suspend fun deleteTrackVolume(trackVolume: TrackVolume) = Unit
        override suspend fun deleteTrackVolumeById(id: Int) = Unit
    }
}
