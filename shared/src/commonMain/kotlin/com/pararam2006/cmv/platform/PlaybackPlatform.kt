package com.pararam2006.cmv.platform

import com.pararam2006.cmv.domain.model.AppInfo
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackStatus {
    PLAYING,
    PAUSED,
    STOPPED,
}

data class MediaPlayerSnapshot(
    val app: AppInfo,
    val instanceId: String,
    val playbackStatus: PlaybackStatus,
    val trackTitle: String?,
    val trackArtist: String?,
    val lastActivitySequence: Long,
)

interface MediaPlaybackMonitor {
    val players: StateFlow<List<MediaPlayerSnapshot>>
    val activePlayer: StateFlow<MediaPlayerSnapshot?>

    suspend fun start()
    suspend fun stop()
}

data class SystemVolumeSnapshot(
    val currentVolume: Int,
    val maxVolume: Int,
    val isMuted: Boolean,
)

interface SystemVolumeController {
    val volume: StateFlow<SystemVolumeSnapshot?>

    suspend fun start()
    suspend fun stop()
    suspend fun setVolume(volume: Int)
}

data class AudioRouteSnapshot(
    val id: String,
    val name: String,
    val isHeadphones: Boolean,
)

interface AudioRouteMonitor {
    val route: StateFlow<AudioRouteSnapshot?>

    suspend fun start()
    suspend fun stop()
}

enum class PlaybackRuntimeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    UNAVAILABLE,
    ERROR,
}

data class PlaybackRuntimeState(
    val status: PlaybackRuntimeStatus = PlaybackRuntimeStatus.STOPPED,
    val message: String? = null,
)

interface PlaybackTrackingRuntime {
    val state: StateFlow<PlaybackRuntimeState>
    val isSupported: Boolean

    fun start(): Boolean
    fun stop(): Boolean
}
