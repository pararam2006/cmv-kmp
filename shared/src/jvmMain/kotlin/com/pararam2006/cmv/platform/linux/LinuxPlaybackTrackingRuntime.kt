package com.pararam2006.cmv.platform.linux

import com.pararam2006.cmv.core.service.ListenerServiceStateHolder
import com.pararam2006.cmv.domain.manager.VolumeCommand
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.domain.service.PlaybackTrackingCoordinator
import com.pararam2006.cmv.platform.AudioRouteMonitor
import com.pararam2006.cmv.platform.MediaPlaybackMonitor
import com.pararam2006.cmv.platform.MediaPlayerSnapshot
import com.pararam2006.cmv.platform.PlaybackRuntimeState
import com.pararam2006.cmv.platform.PlaybackRuntimeStatus
import com.pararam2006.cmv.platform.PlaybackStatus
import com.pararam2006.cmv.platform.PlaybackTrackingRuntime
import com.pararam2006.cmv.platform.SystemVolumeController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class LinuxPlaybackTrackingRuntime(
    private val scope: CoroutineScope,
    private val playbackMonitor: MediaPlaybackMonitor,
    private val volumeController: SystemVolumeController,
    private val routeMonitor: AudioRouteMonitor,
    private val appsInfoRepository: AppsInfoRepository,
    private val coordinator: PlaybackTrackingCoordinator,
    private val serviceStateHolder: ListenerServiceStateHolder,
    private val logger: (String) -> Unit = {},
) : PlaybackTrackingRuntime {
    private val _state = MutableStateFlow(PlaybackRuntimeState())
    override val state: StateFlow<PlaybackRuntimeState> = _state.asStateFlow()
    override val isSupported: Boolean =
        System.getProperty("os.name").contains("linux", ignoreCase = true)

    private val lifecycleLock = Any()
    private var runtimeJob: Job? = null
    private var activeInstanceId: String? = null

    override fun start(): Boolean = synchronized(lifecycleLock) {
        if (!isSupported) {
            _state.value = PlaybackRuntimeState(
                status = PlaybackRuntimeStatus.UNAVAILABLE,
                message = "Playback tracking is currently implemented only for Linux",
            )
            serviceStateHolder.setStarting(false)
            serviceStateHolder.setRestartResult(false)
            return@synchronized false
        }
        if (runtimeJob?.isActive == true) return@synchronized true

        _state.value = PlaybackRuntimeState(PlaybackRuntimeStatus.STARTING)
        serviceStateHolder.setStarting(true)
        serviceStateHolder.setRestartResult(null)
        runtimeJob = scope.launch { runRuntime() }
        true
    }

    override fun stop(): Boolean = synchronized(lifecycleLock) {
        val job = runtimeJob
        runtimeJob = null
        if (job == null) {
            serviceStateHolder.setConnected(false)
            serviceStateHolder.setStarting(false)
            _state.value = PlaybackRuntimeState(PlaybackRuntimeStatus.STOPPED)
            return@synchronized true
        }
        scope.launch {
            job.cancelAndJoin()
        }
        true
    }

    private suspend fun runRuntime() {
        var audioStarted = false
        try {
            playbackMonitor.start()
            volumeController.start()
            routeMonitor.start()
            audioStarted = true

            serviceStateHolder.setConnected(true)
            serviceStateHolder.setStarting(false)
            serviceStateHolder.setRestartResult(true)
            _state.value = PlaybackRuntimeState(
                status = PlaybackRuntimeStatus.RUNNING,
                message = "Waiting for playback from a selected MPRIS application",
            )
            logger("Linux playback runtime started")

            coroutineScope {
                launch { collectSelectedApps() }
                launch { collectActivePlayer() }
                launch { collectRouteChanges() }
                launch { collectVolumeChanges() }
                launch { collectVolumeCommands() }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            reportRuntimeFailure(exception)
        } catch (linkageError: LinkageError) {
            reportRuntimeFailure(linkageError)
        } finally {
            if (audioStarted) {
                runCatching { routeMonitor.stop() }
                runCatching { volumeController.stop() }
            }
            activeInstanceId = null
            coordinator.onServiceStopped()
            clearPlaybackState()
            serviceStateHolder.setConnected(false)
            serviceStateHolder.setStarting(false)
            if (_state.value.status != PlaybackRuntimeStatus.ERROR) {
                _state.value = PlaybackRuntimeState(PlaybackRuntimeStatus.STOPPED)
            }
            logger("Linux playback runtime stopped")
        }
    }

    private fun reportRuntimeFailure(failure: Throwable) {
        logger("Linux playback runtime failed: ${failure.message}")
        _state.value = PlaybackRuntimeState(
            status = PlaybackRuntimeStatus.ERROR,
            message = failure.message ?: "Unable to start Linux playback tracking",
        )
        serviceStateHolder.setRestartResult(false)
    }

    private suspend fun collectSelectedApps() {
        appsInfoRepository.getAllSelectedAppsInfo().collect { apps ->
            serviceStateHolder.setSelectedApps(apps)
            logger(
                "Selected MPRIS apps: " + apps
                    .joinToString { "${it.label} (${it.packageName})" }
                    .ifEmpty { "none" },
            )
        }
    }

    private suspend fun collectActivePlayer() {
        combine(
            playbackMonitor.players,
            appsInfoRepository.getAllSelectedAppsInfo(),
        ) { players, selectedApps ->
            selectActivePlayer(
                players = players,
                selectedPackageNames = selectedApps.mapTo(mutableSetOf()) { it.packageName },
                currentInstanceId = activeInstanceId,
            )
        }
            .distinctUntilChanged()
            .collect(::applyActivePlayer)
    }

    private fun applyActivePlayer(player: MediaPlayerSnapshot?) {
        val previousInstanceId = activeInstanceId
        if (player == null) {
            if (activeInstanceId != null) {
                coordinator.onSessionDetached()
                activeInstanceId = null
                clearPlaybackState()
            }
            return
        }

        if (activeInstanceId != null && activeInstanceId != player.instanceId) {
            coordinator.onSessionDetached()
        }
        activeInstanceId = player.instanceId
        if (previousInstanceId != player.instanceId) {
            logger(
                "Active MPRIS player: ${player.app.label} (${player.app.packageName}), " +
                    "status=${player.playbackStatus}",
            )
        }
        val packageName = player.app.packageName
        val isPlaying = player.playbackStatus == PlaybackStatus.PLAYING
        serviceStateHolder.setActiveSessionPackageName(packageName)
        serviceStateHolder.setCurrentTrackTitle(player.trackTitle)
        serviceStateHolder.setCurrentTrackArtist(player.trackArtist)

        coordinator.onActiveSessionPackageNameChanged(packageName)
        coordinator.onPlaybackStateChanged(isPlaying)
        val currentVolume = volumeController.volume.value
        if (currentVolume != null) {
            coordinator.onTrackMetadataChanged(
                title = player.trackTitle,
                artist = player.trackArtist,
                currentSystemVolume = currentVolume.currentVolume,
                maxVolume = currentVolume.maxVolume,
                hasAudioFocus = isPlaying,
            )
        }
    }

    private suspend fun collectRouteChanges() {
        routeMonitor.route.filterNotNull().collect { route ->
            coordinator.onHeadsetStateChanged(route.isHeadphones)
        }
    }

    private suspend fun collectVolumeChanges() {
        volumeController.volume.filterNotNull().collect { volume ->
            val player = currentActivePlayer()
            coordinator.onVolumeChanged(
                newVolume = volume.currentVolume,
                isHeadset = routeMonitor.route.value?.isHeadphones == true,
                hasAudioFocus = player?.playbackStatus == PlaybackStatus.PLAYING,
            )
        }
    }

    private suspend fun collectVolumeCommands() {
        coordinator.volumeCommands.collect { command ->
            if (!shouldApply(command)) {
                logger("Discarded stale Linux volume command for ${command.trackTitle}")
                return@collect
            }
            val before = volumeController.volume.value?.currentVolume
            volumeController.setVolume(command.targetVolume)
            val applied = volumeController.volume.value?.currentVolume
            logger(
                "Applied Linux volume offset for ${command.trackTitle}: " +
                    "$before -> $applied (target=${command.targetVolume})",
            )
        }
    }

    private suspend fun shouldApply(command: VolumeCommand): Boolean =
        routeMonitor.route.value?.isHeadphones == true &&
            currentActivePlayer()?.playbackStatus == PlaybackStatus.PLAYING &&
            coordinator.isVolumeCommandCurrent(command)

    private fun currentActivePlayer(): MediaPlayerSnapshot? {
        val instanceId = activeInstanceId ?: return null
        return playbackMonitor.players.value.firstOrNull { it.instanceId == instanceId }
    }

    private fun clearPlaybackState() {
        serviceStateHolder.setActiveSessionPackageName(null)
        serviceStateHolder.setCurrentTrackTitle(null)
        serviceStateHolder.setCurrentTrackArtist(null)
    }
}

internal fun selectActivePlayer(
    players: List<MediaPlayerSnapshot>,
    selectedPackageNames: Set<String>,
    currentInstanceId: String?,
): MediaPlayerSnapshot? {
    val selectedPlayers = players.filter { it.app.packageName in selectedPackageNames }
    val current = selectedPlayers.firstOrNull { it.instanceId == currentInstanceId }
    if (current?.playbackStatus == PlaybackStatus.PLAYING ||
        current?.playbackStatus == PlaybackStatus.PAUSED
    ) {
        return current
    }
    return selectedPlayers
        .asSequence()
        .filter { it.playbackStatus == PlaybackStatus.PLAYING }
        .maxByOrNull { it.lastActivitySequence }
}
