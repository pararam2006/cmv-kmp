package com.pararam2006.cmv.domain.service

import com.pararam2006.cmv.domain.manager.VolumeCommand
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.manager.VolumeState
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlayingTrack(
    val title: String,
    val artist: String?,
    val generation: Long,
)

private sealed interface TrackingEvent {
    data class SelectedAppsChanged(val apps: List<AppInfo>) : TrackingEvent
    data class ActiveSessionChanged(val packageName: String?) : TrackingEvent
    data class PlaybackChanged(val isPlaying: Boolean) : TrackingEvent
    data class HeadsetChanged(val isConnected: Boolean) : TrackingEvent
    data class VolumeChanged(
        val systemVolume: SystemVolumeSnapshot,
        val isHeadset: Boolean,
        val hasAudioFocus: Boolean,
    ) : TrackingEvent

    data class TrackMetadataChanged(
        val title: String?,
        val artist: String?,
        val systemVolume: SystemVolumeSnapshot,
        val hasAudioFocus: Boolean,
    ) : TrackingEvent

    data class ValidateVolumeCommand(
        val command: VolumeCommand,
        val result: CompletableDeferred<Boolean>,
    ) : TrackingEvent

    data class Barrier(val result: CompletableDeferred<Unit>) : TrackingEvent

    data object ResetCurrentTrack : TrackingEvent
    data object SessionDetached : TrackingEvent
    data object ServiceStopped : TrackingEvent
}

class PlaybackTrackingCoordinator(
    private val appsInfoRepository: AppsInfoRepository,
    private val trackVolumeRepository: TrackVolumeRepository,
    private val volumeLearningManager: VolumeLearningManager,
    scope: CoroutineScope,
    private val logger: (String) -> Unit = {},
) {
    val selectedApps: Flow<List<AppInfo>> = appsInfoRepository.getAllSelectedAppsInfo()
    val volumeCommands: Flow<VolumeCommand> = volumeLearningManager.volumeCommands
    val debugState: StateFlow<VolumeState> = volumeLearningManager.debugState

    private val events = Channel<TrackingEvent>(Channel.UNLIMITED)
    private val _currentTrack = MutableStateFlow<PlayingTrack?>(null)
    val currentTrack: StateFlow<PlayingTrack?> = _currentTrack.asStateFlow()

    private var activeSessionPackageName: String? = null
    private var selectedPackages: Set<String>? = null
    private var trackGeneration: Long = 0

    init {
        scope.launch {
            for (event in events) {
                try {
                    handleEvent(event)
                } catch (exception: Exception) {
                    logger("Tracking event ${event::class.simpleName} failed: ${exception.message}")
                    completeFailedEvent(event, exception)
                }
            }
        }

        scope.launch {
            selectedApps.collect { apps ->
                events.send(TrackingEvent.SelectedAppsChanged(apps))
            }
        }
    }

    fun onActiveSessionPackageNameChanged(packageName: String?) {
        enqueue(TrackingEvent.ActiveSessionChanged(packageName))
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        enqueue(TrackingEvent.PlaybackChanged(isPlaying))
    }

    fun onHeadsetStateChanged(isConnected: Boolean) {
        enqueue(TrackingEvent.HeadsetChanged(isConnected))
    }

    fun onVolumeChanged(
        systemVolume: SystemVolumeSnapshot,
        isHeadset: Boolean,
        hasAudioFocus: Boolean,
    ) {
        enqueue(
            TrackingEvent.VolumeChanged(
                systemVolume = systemVolume,
                isHeadset = isHeadset,
                hasAudioFocus = hasAudioFocus,
            ),
        )
    }

    fun onTrackMetadataChanged(
        title: String?,
        artist: String?,
        systemVolume: SystemVolumeSnapshot,
        hasAudioFocus: Boolean,
    ) {
        enqueue(
            TrackingEvent.TrackMetadataChanged(
                title = title,
                artist = artist,
                systemVolume = systemVolume,
                hasAudioFocus = hasAudioFocus,
            ),
        )
    }

    fun resetCurrentTrack() {
        enqueue(TrackingEvent.ResetCurrentTrack)
    }

    fun onSessionDetached() {
        enqueue(TrackingEvent.SessionDetached)
    }

    fun onServiceStopped() {
        enqueue(TrackingEvent.ServiceStopped)
    }

    suspend fun isVolumeCommandCurrent(command: VolumeCommand): Boolean {
        val result = CompletableDeferred<Boolean>()
        events.send(TrackingEvent.ValidateVolumeCommand(command, result))
        return result.await()
    }

    suspend fun awaitIdle() {
        val result = CompletableDeferred<Unit>()
        events.send(TrackingEvent.Barrier(result))
        result.await()
    }

    private suspend fun handleEvent(event: TrackingEvent) {
        when (event) {
            is TrackingEvent.SelectedAppsChanged -> handleSelectedAppsChanged(event.apps)
            is TrackingEvent.ActiveSessionChanged -> handleActiveSessionChanged(event.packageName)
            is TrackingEvent.PlaybackChanged -> handlePlaybackStateChanged(event.isPlaying)
            is TrackingEvent.HeadsetChanged -> volumeLearningManager.onHeadsetStateChanged(event.isConnected)
            is TrackingEvent.VolumeChanged -> handleVolumeChanged(event)
            is TrackingEvent.TrackMetadataChanged -> handleTrackMetadataChanged(event)
            is TrackingEvent.ValidateVolumeCommand -> event.result.complete(
                validateVolumeCommand(event.command),
            )
            is TrackingEvent.Barrier -> event.result.complete(Unit)
            TrackingEvent.ResetCurrentTrack -> resetTrackSnapshot()
            TrackingEvent.SessionDetached -> detachSession(clearActivePackage = true)
            TrackingEvent.ServiceStopped -> {
                detachSession(clearActivePackage = true)
                volumeLearningManager.onServiceStopped()
            }
        }
    }

    private suspend fun handleSelectedAppsChanged(apps: List<AppInfo>) {
        selectedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
        if (!isActiveAppSelected() && _currentTrack.value != null) {
            logger("Active app $activeSessionPackageName was unselected; detaching track")
            detachSession(clearActivePackage = false)
        }
    }

    private fun handleActiveSessionChanged(packageName: String?) {
        if (packageName == activeSessionPackageName) return

        if (activeSessionPackageName != null || _currentTrack.value != null) {
            detachSession(clearActivePackage = false)
        }
        activeSessionPackageName = packageName
        volumeLearningManager.onActiveSessionPackageNameChanged(packageName)
    }

    private suspend fun handlePlaybackStateChanged(isPlaying: Boolean) {
        ensureSelectedPackagesLoaded()
        volumeLearningManager.onPlaybackStateChanged(isPlaying && isActiveAppSelected())
    }

    private suspend fun handleVolumeChanged(event: TrackingEvent.VolumeChanged) {
        ensureSelectedPackagesLoaded()
        if (!isActiveAppSelected()) {
            logger("Volume change ignored: $activeSessionPackageName is not selected")
            return
        }

        volumeLearningManager.onHeadsetStateChanged(event.isHeadset)
        volumeLearningManager.onAudioFocusChanged(event.hasAudioFocus)
        volumeLearningManager.onVolumeChanged(event.systemVolume)
    }

    private suspend fun handleTrackMetadataChanged(event: TrackingEvent.TrackMetadataChanged) {
        val title = event.title ?: return
        ensureSelectedPackagesLoaded()
        if (!isActiveAppSelected()) {
            logger("Metadata ignored: $activeSessionPackageName is not selected")
            return
        }

        val oldTrack = _currentTrack.value
        if (oldTrack?.title == title && oldTrack.artist == event.artist) return

        val rule = trackVolumeRepository.getTrackVolume(title, event.artist)
        val offset = rule?.volumeOffsetDb ?: 0f
        val offsetModel = rule?.offsetModel ?: VolumeOffsetModel.DECIBEL

        trackGeneration += 1
        val newTrack = PlayingTrack(title, event.artist, trackGeneration)
        _currentTrack.value = newTrack

        logger(
            "New track: title=$title, artist=${event.artist}, " +
                "offset=$offset/$offsetModel, volume=${event.systemVolume.currentVolumeDb}dB " +
                "(${event.systemVolume.currentVolume}/${event.systemVolume.maxVolume}), " +
                "focus=${event.hasAudioFocus}, generation=$trackGeneration",
        )
        volumeLearningManager.onAudioFocusChanged(event.hasAudioFocus)
        volumeLearningManager.onTrackChanged(
            title = title,
            artist = event.artist,
            volumeOffset = offset,
            offsetModel = offsetModel,
            systemVolume = event.systemVolume,
            trackGeneration = trackGeneration,
        )
    }

    private fun detachSession(clearActivePackage: Boolean) {
        trackGeneration += 1
        _currentTrack.value = null
        volumeLearningManager.onPlaybackStateChanged(false)
        volumeLearningManager.onSessionDetached()
        if (clearActivePackage) {
            activeSessionPackageName = null
            volumeLearningManager.onActiveSessionPackageNameChanged(null)
        }
    }

    private fun resetTrackSnapshot() {
        trackGeneration += 1
        _currentTrack.value = null
    }

    private fun validateVolumeCommand(command: VolumeCommand): Boolean {
        val track = _currentTrack.value
        val state = debugState.value
        return isActiveAppSelected() &&
            track?.title == command.trackTitle &&
            track.artist == command.trackArtist &&
            track.generation == command.trackGeneration &&
            state.trackGeneration == command.trackGeneration &&
            state.isPlaying &&
            state.isHeadsetConnected &&
            state.hasAudioFocus
    }

    private suspend fun ensureSelectedPackagesLoaded() {
        if (selectedPackages == null) {
            selectedPackages = selectedApps.first().mapTo(mutableSetOf()) { it.packageName }
        }
    }

    private fun isActiveAppSelected(): Boolean {
        val packageName = activeSessionPackageName ?: return false
        return packageName in selectedPackages.orEmpty()
    }

    private fun enqueue(event: TrackingEvent) {
        if (events.trySend(event).isFailure) {
            logger("Tracking event ${event::class.simpleName} was dropped")
        }
    }

    private fun completeFailedEvent(event: TrackingEvent, exception: Exception) {
        when (event) {
            is TrackingEvent.ValidateVolumeCommand -> event.result.completeExceptionally(exception)
            is TrackingEvent.Barrier -> event.result.completeExceptionally(exception)
            else -> Unit
        }
    }
}
