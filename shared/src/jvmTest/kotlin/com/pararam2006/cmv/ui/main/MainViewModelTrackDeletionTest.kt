package com.pararam2006.cmv.ui.main

import androidx.lifecycle.viewModelScope
import com.pararam2006.cmv.core.Constants
import com.pararam2006.cmv.core.service.MyNotificationListenerServiceStateHolder
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.domain.usecase.DeleteTrackVolumeUseCase
import com.pararam2006.cmv.domain.usecase.GetTrackVolumesUseCase
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.platform.SettingsPreferences
import com.pararam2006.cmv.platform.SystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTrackDeletionTest {
    @Test
    fun manualRuleCanBeCreatedWithoutSelectAppsScreen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = RecordingTrackRepository(emptyList())
        val viewModel = createViewModel(repository)
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.mainScreenUiState.collect()
        }

        try {
            runCurrent()
            viewModel.openAddDialog()
            viewModel.changeEditTitle("Manual track")
            viewModel.changeEditArtist("Manual artist")
            viewModel.changeEditOffset(4.5f)
            runCurrent()

            val draft = assertNotNull(viewModel.mainScreenUiState.value.dialogTrack)
            assertEquals("Manual track", draft.trackTitle)
            assertEquals("Manual artist", draft.artistName)
            assertEquals(4.5f, draft.volumeOffsetDb)

            viewModel.saveTrackVolume(
                title = draft.trackTitle,
                artist = draft.artistName,
                offset = draft.volumeOffsetDb,
            )
            runCurrent()

            assertEquals(
                listOf(
                    TrackVolume(
                        trackTitle = "Manual track",
                        artistName = "Manual artist",
                        volumeOffsetDb = 4.5f,
                        offsetModel = VolumeOffsetModel.DECIBEL,
                    ),
                ),
                repository.savedTracks,
            )
        } finally {
            collectorJob.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deletionCompletesWithoutTrackItemBeingComposed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = RecordingTrackRepository(tracks())
        val viewModel = createViewModel(repository)
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.mainScreenUiState.collect()
        }

        try {
            runCurrent()
            viewModel.startTrackDelete(1)
            runCurrent()

            assertEquals(1f, viewModel.mainScreenUiState.value.trackDeletionProgress[1])

            advanceTimeBy((Constants.TRACK_DELETE_UNDO_TIMEOUT / 2).milliseconds)
            runCurrent()

            val progress = assertNotNull(
                viewModel.mainScreenUiState.value.trackDeletionProgress[1]
            )
            assertTrue(progress in 0.45f..0.55f)
            assertTrue(repository.deletedIds.isEmpty())

            advanceTimeBy((Constants.TRACK_DELETE_UNDO_TIMEOUT / 2).milliseconds)
            runCurrent()

            assertEquals(listOf(1), repository.deletedIds)
            assertFalse(1 in viewModel.mainScreenUiState.value.trackDeletionProgress)
        } finally {
            collectorJob.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellationPreventsDeletionAfterTimeout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = RecordingTrackRepository(tracks())
        val viewModel = createViewModel(repository)
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.mainScreenUiState.collect()
        }

        try {
            runCurrent()
            viewModel.startTrackDelete(1)
            advanceTimeBy((Constants.TRACK_DELETE_UNDO_TIMEOUT / 2).milliseconds)
            runCurrent()

            viewModel.cancelTrackDelete(1)
            runCurrent()
            advanceTimeBy(Constants.TRACK_DELETE_UNDO_TIMEOUT.milliseconds)
            runCurrent()

            assertTrue(repository.deletedIds.isEmpty())
            assertFalse(1 in viewModel.mainScreenUiState.value.trackDeletionProgress)
        } finally {
            collectorJob.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(repository: TrackVolumeRepository) = MainViewModel(
        getTrackVolumesUseCase = GetTrackVolumesUseCase(repository),
        saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
        deleteTrackVolumeUseCase = DeleteTrackVolumeUseCase(repository),
        systemService = SystemService(),
        settingsPreferences = SettingsPreferences(),
        serviceStateHolder = MyNotificationListenerServiceStateHolder(),
        headphonesDetector = FakeHeadphonesRepository(),
    )

    private fun tracks() = listOf(
        TrackVolume(
            id = 1,
            trackTitle = "First",
            artistName = "Artist",
            volumeOffsetDb = 0f,
        )
    )

    private class FakeHeadphonesRepository : HeadphonesRepository {
        private val state = MutableSharedFlow<Boolean>(replay = 1).apply {
            tryEmit(true)
        }
        override val isHeadsetFlow: SharedFlow<Boolean> = state
        override fun computeIsHeadsetConnected(): Boolean = true
    }

    private class RecordingTrackRepository(
        initialTracks: List<TrackVolume>,
    ) : TrackVolumeRepository {
        private val tracks = MutableStateFlow(initialTracks)
        val deletedIds = mutableListOf<Int>()
        val savedTracks: List<TrackVolume> get() = tracks.value

        override fun getAllTrackVolumes(): Flow<List<TrackVolume>> = tracks
        override suspend fun getTrackVolume(title: String, artist: String?): TrackVolume? =
            tracks.value.firstOrNull {
                it.trackTitle == title && it.artistName == artist
            }

        override suspend fun getTrackVolumeById(id: Int): TrackVolume? =
            tracks.value.firstOrNull { it.id == id }

        override suspend fun saveTrackVolume(trackVolume: TrackVolume) {
            tracks.update { current ->
                current.filterNot { it.id == trackVolume.id } + trackVolume
            }
        }

        override suspend fun deleteTrackVolume(trackVolume: TrackVolume) {
            deleteTrackVolumeById(trackVolume.id)
        }

        override suspend fun deleteTrackVolumeById(id: Int) {
            deletedIds += id
            tracks.update { current -> current.filterNot { it.id == id } }
        }
    }
}
