package com.pararam2006.cmv.domain.service

import com.pararam2006.cmv.domain.manager.VolumeCommand
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.manager.VolumeState
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTrackingCoordinatorTest {
    @Test
    fun volumeChangesAreForwardedOnlyForSelectedApp() = runTest {
        val manager = RecordingManager()
        val apps = FakeAppsInfoRepository(
            listOf(appInfo("selected.app", selected = true)),
        )
        val coordinator = PlaybackTrackingCoordinator(
            appsInfoRepository = apps,
            trackVolumeRepository = FakeTrackVolumeRepository(),
            volumeLearningManager = manager,
            scope = backgroundScope,
        )

        coordinator.onActiveSessionPackageNameChanged("other.app")
        coordinator.onVolumeChanged(snapshot(4), isHeadset = true, hasAudioFocus = true)
        coordinator.onActiveSessionPackageNameChanged("selected.app")
        coordinator.onVolumeChanged(snapshot(7), isHeadset = true, hasAudioFocus = true)
        coordinator.awaitIdle()

        assertEquals(listOf(7f), manager.volumeChanges)
    }

    @Test
    fun duplicateMetadataIsAppliedOnlyOnce() = runTest {
        val manager = RecordingManager()
        val tracks = FakeTrackVolumeRepository(
            TrackVolume(
                trackTitle = "Track",
                artistName = "Artist",
                volumeOffsetDb = 1.5f,
                offsetModel = VolumeOffsetModel.DECIBEL,
            ),
        )
        val coordinator = PlaybackTrackingCoordinator(
            appsInfoRepository = FakeAppsInfoRepository(
                listOf(appInfo("selected.app", selected = true)),
            ),
            trackVolumeRepository = tracks,
            volumeLearningManager = manager,
            scope = backgroundScope,
        )

        coordinator.onActiveSessionPackageNameChanged("selected.app")
        coordinator.onTrackMetadataChanged("Track", "Artist", snapshot(5), true)
        coordinator.onTrackMetadataChanged("Track", "Artist", snapshot(5), true)
        coordinator.awaitIdle()

        assertEquals(1, manager.trackChanges.size)
        assertEquals(1.5f, manager.trackChanges.single().offset)
    }

    @Test
    fun metadataFromUnselectedAppIsIgnored() = runTest {
        val manager = RecordingManager()
        val coordinator = PlaybackTrackingCoordinator(
            appsInfoRepository = FakeAppsInfoRepository(
                listOf(appInfo("selected.app", selected = true)),
            ),
            trackVolumeRepository = FakeTrackVolumeRepository(),
            volumeLearningManager = manager,
            scope = backgroundScope,
        )

        coordinator.onActiveSessionPackageNameChanged("other.app")
        coordinator.onTrackMetadataChanged("Track", "Artist", snapshot(5), true)
        coordinator.awaitIdle()

        assertEquals(0, manager.trackChanges.size)
    }

    @Test
    fun sameMetadataIsAppliedAgainAfterSessionDetach() = runTest {
        val manager = RecordingManager()
        val coordinator = PlaybackTrackingCoordinator(
            appsInfoRepository = FakeAppsInfoRepository(
                listOf(appInfo("selected.app", selected = true)),
            ),
            trackVolumeRepository = FakeTrackVolumeRepository(),
            volumeLearningManager = manager,
            scope = backgroundScope,
        )

        coordinator.onActiveSessionPackageNameChanged("selected.app")
        coordinator.onTrackMetadataChanged("Track", "Artist", snapshot(5), true)
        coordinator.onSessionDetached()
        coordinator.onActiveSessionPackageNameChanged("selected.app")
        coordinator.onTrackMetadataChanged("Track", "Artist", snapshot(5), true)
        coordinator.awaitIdle()

        assertEquals(2, manager.trackChanges.size)
        assertEquals(1, manager.sessionDetachCount)
    }

    private fun snapshot(volume: Int, maxVolume: Int = 15): SystemVolumeSnapshot =
        SystemVolumeSnapshot(
            currentVolume = volume,
            maxVolume = maxVolume,
            isMuted = false,
            volumeDbByStep = List(maxVolume + 1) { it.toFloat() },
        )

    private data class TrackChange(val title: String, val offset: Float)

    private class RecordingManager : VolumeLearningManager {
        override val volumeCommands = emptyFlow<VolumeCommand>()
        override val debugState: StateFlow<VolumeState> = MutableStateFlow(VolumeState())
        val volumeChanges = mutableListOf<Float>()
        val trackChanges = mutableListOf<TrackChange>()
        var sessionDetachCount = 0

        override fun onActiveSessionPackageNameChanged(newPackageName: String?) = Unit

        override fun onTrackChanged(
            title: String,
            artist: String?,
            volumeOffset: Float,
            offsetModel: VolumeOffsetModel,
            systemVolume: SystemVolumeSnapshot,
            trackGeneration: Long,
        ) {
            trackChanges += TrackChange(title, volumeOffset)
        }

        override fun onVolumeChanged(systemVolume: SystemVolumeSnapshot) {
            volumeChanges += systemVolume.currentVolumeDb
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit
        override fun onHeadsetStateChanged(isConnected: Boolean) = Unit
        override fun onAudioFocusChanged(hasFocus: Boolean) = Unit
        override fun onSessionDetached() { sessionDetachCount += 1 }
        override fun onServiceStopped() = Unit
        override fun onAppModeChanged(newMode: AppMode) = Unit
    }

    private class FakeAppsInfoRepository(
        initial: List<AppInfo>,
    ) : AppsInfoRepository {
        private val apps = MutableStateFlow(initial)

        override fun getAllAppsInfo(): Flow<List<AppInfo>> = apps
        override fun getAllSelectedAppsInfo(): Flow<List<AppInfo>> =
            MutableStateFlow(apps.value.filter { it.selected })

        override suspend fun getAppInfo(packageName: String): AppInfo? =
            apps.value.firstOrNull { it.packageName == packageName }

        override suspend fun getAppInfo(id: Int): AppInfo? = null
        override suspend fun selectApp(id: Int) = Unit
        override suspend fun selectApp(packageName: String) = Unit
        override suspend fun unselectApp(id: Int) = Unit
        override suspend fun unselectApp(packageName: String) = Unit
        override suspend fun addAppInfo(appInfo: AppInfo) = Unit
        override suspend fun deleteAppInfo(appInfo: AppInfo) = Unit
        override suspend fun deleteAppInfo(id: Int) = Unit
    }

    private class FakeTrackVolumeRepository(
        private var track: TrackVolume? = null,
    ) : TrackVolumeRepository {
        override fun getAllTrackVolumes(): Flow<List<TrackVolume>> =
            MutableStateFlow(listOfNotNull(track))

        override suspend fun getTrackVolume(title: String, artist: String?): TrackVolume? = track
        override suspend fun getTrackVolumeById(id: Int): TrackVolume? = track
        override suspend fun saveTrackVolume(trackVolume: TrackVolume) {
            track = trackVolume
        }

        override suspend fun deleteTrackVolume(trackVolume: TrackVolume) {
            track = null
        }

        override suspend fun deleteTrackVolumeById(id: Int) {
            track = null
        }
    }

    private fun appInfo(packageName: String, selected: Boolean) = AppInfo(
        label = packageName,
        iconUri = "",
        packageName = packageName,
        name = packageName,
        selected = selected,
    )
}
