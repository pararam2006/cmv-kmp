package com.pararam2006.cmv.data.manager

import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
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
            volumeJumpProtectionEnabled = { false },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onTrackChanged(
            title = "Track",
            artist = "Artist",
            volumeOffset = 0f,
            offsetModel = VolumeOffsetModel.DECIBEL,
            systemVolume = snapshot(5),
            trackGeneration = 1,
        )
        manager.onPlaybackStateChanged(true)
        runCurrent()

        now = 2_000L
        manager.onVolumeChanged(snapshot(7))
        runCurrent()

        assertTrue(manager.debugState.value.hasLearnedOffsetChanged)
        assertEquals(2f, manager.debugState.value.currentLearnedOffsetDb)

        now = 17_000L
        manager.onPlaybackStateChanged(false)
        runCurrent()

        assertFalse(manager.debugState.value.hasLearnedOffsetChanged)
        assertEquals(1, repository.tracks.value.size)
        assertEquals(2f, repository.tracks.value.single().volumeOffsetDb)
        assertEquals(VolumeOffsetModel.DECIBEL, repository.tracks.value.single().offsetModel)
    }

    @Test
    fun immatureOffsetIsDiscardedWithoutSavingWhenSessionDetaches() = runTest {
        val repository = FakeTrackVolumeRepository()
        var now = 1_000L
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { false },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onAudioFocusChanged(true)
        manager.onPlaybackStateChanged(true)
        manager.onTrackChanged("Track", "Artist", 0f, VolumeOffsetModel.DECIBEL, snapshot(5), 1)
        runCurrent()
        now = 2_000L
        manager.onVolumeChanged(snapshot(7))
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
            volumeJumpProtectionEnabled = { false },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onAudioFocusChanged(true)
        manager.onPlaybackStateChanged(true)
        manager.onTrackChanged("Track", "Artist", 2f, VolumeOffsetModel.DECIBEL, snapshot(5), 2)
        runCurrent()
        now = 2_000L
        manager.onVolumeChanged(snapshot(5))
        runCurrent()
        now = 17_000L
        manager.onPlaybackStateChanged(false)
        runCurrent()

        assertEquals(1, repository.tracks.value.size)
        assertEquals(0f, repository.tracks.value.single().volumeOffsetDb)
    }

    @Test
    fun volumeChangeIsNotLearnedWhilePlaybackIsStopped() = runTest {
        val repository = FakeTrackVolumeRepository()
        var now = 1_000L
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { false },
            scope = backgroundScope,
            nowMillis = { now },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onAudioFocusChanged(true)
        manager.onTrackChanged("Track", "Artist", 0f, VolumeOffsetModel.DECIBEL, snapshot(5), 3)
        runCurrent()
        now = 2_000L
        manager.onVolumeChanged(snapshot(7))
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
            volumeJumpProtectionEnabled = { false },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onPlaybackStateChanged(true)
        manager.onTrackChanged(
            title = "Track",
            artist = "Artist",
            volumeOffset = 10f,
            offsetModel = VolumeOffsetModel.DECIBEL,
            systemVolume = snapshot(40, 100),
            trackGeneration = 4,
        )
        runCurrent()
        assertTrue(manager.debugState.value.expectedProgrammaticVolumeDb.isNaN())

        val command = async { manager.volumeCommands.first() }
        manager.onHeadsetStateChanged(true)
        runCurrent()

        assertEquals(50f, command.await().targetVolumeDb)
        assertEquals(50f, manager.debugState.value.expectedProgrammaticVolumeDb)

        manager.onVolumeChanged(snapshot(50, 100))
        runCurrent()
        assertTrue(manager.debugState.value.expectedProgrammaticVolumeDb.isNaN())
        assertFalse(manager.debugState.value.hasLearnedOffsetChanged)
    }

    @Test
    fun resetsCarriedBoostForNeutralTrackWhenProtectionIsEnabled() = runTest {
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(FakeTrackVolumeRepository()),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { true },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onAudioFocusChanged(true)
        manager.onPlaybackStateChanged(true)
        val boostCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Quiet track", "Artist", 8f, VolumeOffsetModel.DECIBEL, snapshot(40, 100), 6)
        runCurrent()
        assertEquals(48f, boostCommand.await().targetVolumeDb)

        manager.onVolumeChanged(snapshot(48, 100))
        runCurrent()
        val protectionCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Loud track", "Artist", 0f, VolumeOffsetModel.DECIBEL, snapshot(48, 100), 7)
        runCurrent()

        assertEquals(40f, protectionCommand.await().targetVolumeDb)
        assertTrue(manager.debugState.value.volumeJumpProtectionApplied)
        assertEquals(8f, manager.debugState.value.previousTrackOffsetDb)
        assertEquals(40f, manager.debugState.value.volumeJumpProtectionTargetDb)
        assertEquals(0f, manager.debugState.value.currentLearnedOffsetDb)
    }

    @Test
    fun leavesCarriedBoostUntouchedWhenProtectionIsDisabled() = runTest {
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(FakeTrackVolumeRepository()),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { false },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onPlaybackStateChanged(true)
        val boostCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Quiet track", "Artist", 8f, VolumeOffsetModel.DECIBEL, snapshot(40, 100), 8)
        runCurrent()
        boostCommand.await()

        manager.onVolumeChanged(snapshot(48, 100))
        runCurrent()
        val unexpectedCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Loud track", "Artist", 0f, VolumeOffsetModel.DECIBEL, snapshot(48, 100), 9)
        runCurrent()

        assertFalse(unexpectedCommand.isCompleted)
        unexpectedCommand.cancel()
        assertFalse(manager.debugState.value.volumeJumpProtectionApplied)
        assertTrue(manager.debugState.value.expectedProgrammaticVolumeDb.isNaN())
    }

    @Test
    fun doesNotProtectOffsetBelowHighOffsetThreshold() = runTest {
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(FakeTrackVolumeRepository()),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { true },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onPlaybackStateChanged(true)
        val boostCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Moderately quiet", "Artist", 5f, VolumeOffsetModel.DECIBEL, snapshot(40, 100), 10)
        runCurrent()
        boostCommand.await()

        manager.onVolumeChanged(snapshot(45, 100))
        runCurrent()
        val unexpectedCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Next track", "Artist", 0f, VolumeOffsetModel.DECIBEL, snapshot(45, 100), 11)
        runCurrent()

        assertFalse(unexpectedCommand.isCompleted)
        unexpectedCommand.cancel()
        assertFalse(manager.debugState.value.volumeJumpProtectionApplied)
    }

    @Test
    fun doesNotProtectCarriedBoostInRegulationMode() = runTest {
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(FakeTrackVolumeRepository()),
            appModeFlow = MutableStateFlow(AppMode.JUST_CHANGING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { true },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onPlaybackStateChanged(true)
        val boostCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Quiet track", "Artist", 8f, VolumeOffsetModel.DECIBEL, snapshot(40, 100), 12)
        runCurrent()
        boostCommand.await()

        manager.onVolumeChanged(snapshot(48, 100))
        runCurrent()
        val unexpectedCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Loud track", "Artist", 0f, VolumeOffsetModel.DECIBEL, snapshot(48, 100), 13)
        runCurrent()

        assertFalse(unexpectedCommand.isCompleted)
        unexpectedCommand.cancel()
        assertFalse(manager.debugState.value.volumeJumpProtectionApplied)
    }

    @Test
    fun savedRuleOfNextTrackHasPriorityOverJumpProtection() = runTest {
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(FakeTrackVolumeRepository()),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { true },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onPlaybackStateChanged(true)
        val boostCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Quiet track", "Artist", 8f, VolumeOffsetModel.DECIBEL, snapshot(40, 100), 14)
        runCurrent()
        boostCommand.await()

        manager.onVolumeChanged(snapshot(48, 100))
        runCurrent()
        val nextRuleCommand = async { manager.volumeCommands.first() }
        manager.onTrackChanged("Known track", "Artist", -2f, VolumeOffsetModel.DECIBEL, snapshot(48, 100), 15)
        runCurrent()

        assertEquals(38f, nextRuleCommand.await().targetVolumeDb)
        assertFalse(manager.debugState.value.volumeJumpProtectionApplied)
        assertEquals(-2f, manager.debugState.value.currentLearnedOffsetDb)
    }

    @Test
    fun legacyRatioIsMigratedThroughTheCurrentPlatformCurve() = runTest {
        val repository = FakeTrackVolumeRepository()
        val manager = VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = SaveTrackVolumeUseCase(repository),
            appModeFlow = MutableStateFlow(AppMode.LEARNING),
            learningTimeSeconds = { 15 },
            volumeJumpProtectionEnabled = { false },
            scope = backgroundScope,
            nowMillis = { 1_000L },
        )
        runCurrent()

        manager.onHeadsetStateChanged(true)
        manager.onPlaybackStateChanged(true)
        manager.onTrackChanged(
            title = "Legacy",
            artist = "Artist",
            volumeOffset = 1.5f,
            offsetModel = VolumeOffsetModel.LEGACY_RATIO,
            systemVolume = snapshot(40, 100),
            trackGeneration = 5,
        )
        runCurrent()

        val migrated = repository.tracks.value.single()
        assertEquals(21f, migrated.volumeOffsetDb)
        assertEquals(VolumeOffsetModel.DECIBEL, migrated.offsetModel)
        assertEquals(61f, manager.volumeCommands.first().targetVolumeDb)
    }

    private fun snapshot(volume: Int, maxVolume: Int = 15): SystemVolumeSnapshot =
        SystemVolumeSnapshot(
            currentVolume = volume,
            maxVolume = maxVolume,
            isMuted = false,
            volumeDbByStep = List(maxVolume + 1) { it.toFloat() },
        )

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
