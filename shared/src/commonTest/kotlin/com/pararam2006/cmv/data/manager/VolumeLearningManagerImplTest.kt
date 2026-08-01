package com.pararam2006.cmv.data.manager

import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.domain.model.AppMode
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeLearningManagerImplTest {
    @Test
    fun learnsAndSavesTrackOffsetWithInjectedClock() = runTest {
        val repository = FakeTrackVolumeRepository()
        val appMode = MutableStateFlow(AppMode.LEARNING)
        var now = 1_000L
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
            appModeFlow = appMode,
            learningTimeSeconds = { 15 },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onTrackChanged(
            title = "Track",
            artist = "Artist",
            offsetFromDb = 1f,
            currentSystemVolume = 5,
            maxVolume = 15,
            trackGeneration = 1,
        )
        manager.onPlaybackStateChanged(true)
        runCurrent()

        now = 2_000L
        manager.onVolumeChanged(7)
        runCurrent()

        assertTrue(manager.debugState.value.hasLearnedOffsetChanged)
        assertEquals(8f / 6f, manager.debugState.value.currentLearnedOffset)

        now = 17_000L
        manager.onPlaybackStateChanged(false)
        runCurrent()

        assertFalse(manager.debugState.value.hasLearnedOffsetChanged)
        assertEquals(1, repository.tracks.value.size)
        assertEquals(8f / 6f, repository.tracks.value.single().volumeOffset)
    }

    @Test
    fun immatureOffsetIsDiscardedWithoutSavingWhenSessionDetaches() = runTest {
        val repository = FakeTrackVolumeRepository()
        var now = 1_000L
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onAudioFocusChanged(true)
        manager.onPlaybackStateChanged(true)
        manager.onTrackChanged("Track", "Artist", 1f, 5, 15, 1)
        runCurrent()
        now = 2_000L
        manager.onVolumeChanged(7)
        runCurrent()
        now = 5_000L
        manager.onPlaybackStateChanged(false)
        manager.onSessionDetached()
        runCurrent()

        assertTrue(repository.tracks.value.isEmpty())
        assertFalse(manager.debugState.value.hasLearnedOffsetChanged)
        assertEquals(null, manager.debugState.value.currentTrackTitle)
    }

    @Test
    fun learnedNeutralOffsetIsSavedAfterThreshold() = runTest {
        val repository = FakeTrackVolumeRepository()
        var now = 1_000L
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onAudioFocusChanged(true)
        manager.onPlaybackStateChanged(true)
        manager.onTrackChanged("Track", "Artist", 1.25f, 5, 15, 2)
        runCurrent()
        now = 2_000L
        manager.onVolumeChanged(5)
        runCurrent()
        now = 17_000L
        manager.onPlaybackStateChanged(false)
        runCurrent()

        assertEquals(1, repository.tracks.value.size)
        assertEquals(1f, repository.tracks.value.single().volumeOffset)
    }

    @Test
    fun volumeChangeIsNotLearnedWhilePlaybackIsStopped() = runTest {
        val repository = FakeTrackVolumeRepository()
        var now = 1_000L
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onAudioFocusChanged(true)
        manager.onTrackChanged("Track", "Artist", 1f, 5, 15, 3)
        runCurrent()
        now = 2_000L
        manager.onVolumeChanged(7)
        runCurrent()

        assertFalse(manager.debugState.value.hasLearnedOffsetChanged)
        assertTrue(repository.tracks.value.isEmpty())
    }

    @Test
    fun appliesLoadedOffsetWhenHeadphonesBecomeAvailableAfterMetadata() = runTest {
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(FakeTrackVolumeRepository()),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onPlaybackStateChanged(true)
        manager.onTrackChanged(
            title = "Track",
            artist = "Artist",
            offsetFromDb = 1.25f,
            currentSystemVolume = 40,
            maxVolume = 100,
            trackGeneration = 4,
        )
        runCurrent()
        assertEquals(-1, manager.debugState.value.expectedProgrammaticVolume)

        val command = async { manager.volumeCommands.first() }
        manager.onHeadsetStateChanged(true)
        runCurrent()

        assertEquals(50, command.await().targetVolume)
        assertEquals(50, manager.debugState.value.expectedProgrammaticVolume)

        manager.onVolumeChanged(50)
        runCurrent()
        assertEquals(-1, manager.debugState.value.expectedProgrammaticVolume)
        assertFalse(manager.debugState.value.hasLearnedOffsetChanged)
    }

    private class FakeTrackVolumeRepository : TrackVolumeRepository {
        val tracks = MutableStateFlow<List<TrackVolume>>(emptyList())

        override fun getAllTrackVolumes(): Flow<List<TrackVolume>> = tracks

        override suspend fun getTrackVolume(title: String, artist: String?): TrackVolume? =
            tracks.value.firstOrNull {
                it.trackTitle == title && it.artistName == artist
            }

        override suspend fun getTrackVolumeById(id: Int): TrackVolume? =
            tracks.value.firstOrNull { it.id == id }

        override suspend fun saveTrackVolume(trackVolume: TrackVolume) {
            tracks.value = tracks.value.filterNot {
                it.trackTitle == trackVolume.trackTitle && it.artistName == trackVolume.artistName
            } + trackVolume
        }

        override suspend fun deleteTrackVolume(trackVolume: TrackVolume) {
            tracks.value = tracks.value - trackVolume
        }

        override suspend fun deleteTrackVolumeById(id: Int) {
            tracks.value = tracks.value.filterNot { it.id == id }
        }
    }
}
