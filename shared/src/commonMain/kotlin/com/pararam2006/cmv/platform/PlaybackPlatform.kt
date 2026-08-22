package com.pararam2006.cmv.platform

import com.pararam2006.cmv.domain.model.AppInfo
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val volumeDbByStep: List<Float> = fallbackVolumeDbCurve(maxVolume),
) {
    init {
        require(maxVolume >= 0)
        require(volumeDbByStep.size == maxVolume + 1) {
            "Expected ${maxVolume + 1} dB values, got ${volumeDbByStep.size}"
        }
    }

    val currentVolumeDb: Float
        get() = dbForNativeVolume(currentVolume)

    val minVolumeDb: Float
        get() = volumeDbByStep.first()

    val maxVolumeDb: Float
        get() = volumeDbByStep.last()

    val volumeStepDb: Float
        get() {
            if (maxVolume == 0) return 0f
            val index = currentVolume.coerceIn(0, maxVolume)
            val currentDb = volumeDbByStep[index]
            val localSteps = buildList {
                if (index > 0) add(abs(currentDb - volumeDbByStep[index - 1]))
                if (index < maxVolume) add(abs(volumeDbByStep[index + 1] - currentDb))
            }
            return localSteps
                .filter { it.isFinite() && it in MIN_ECHO_TOLERANCE_DB..MAX_REASONABLE_STEP_DB }
                .minOrNull()
                ?: MIN_ECHO_TOLERANCE_DB
        }

    fun dbForNativeVolume(volume: Int): Float =
        volumeDbByStep[volume.coerceIn(0, maxVolume)]

    fun nativeVolumeForDb(volumeDb: Float): Int =
        volumeDbByStep.indices.minByOrNull { abs(volumeDbByStep[it] - volumeDb) } ?: 0

    fun clampDb(volumeDb: Float): Float = volumeDb.coerceIn(minVolumeDb, maxVolumeDb)

    fun legacyRatioToOffsetDb(baseNativeVolume: Int, ratio: Float): Float {
        if (ratio <= 0f) return minVolumeDb - dbForNativeVolume(baseNativeVolume)
        val target = (((baseNativeVolume + 1f) * ratio) - 1f)
            .roundToInt()
            .coerceIn(0, maxVolume)
        return dbForNativeVolume(target) - dbForNativeVolume(baseNativeVolume)
    }

    companion object {
        const val MIN_ECHO_TOLERANCE_DB = 0.1f
        private const val MAX_REASONABLE_STEP_DB = 24f
    }
}

private fun fallbackVolumeDbCurve(maxVolume: Int): List<Float> {
    if (maxVolume <= 0) return listOf(-80f)
    return List(maxVolume + 1) { index ->
        if (index == 0) -80f else -60f + (60f * index / maxVolume)
    }
}

interface SystemVolumeController {
    val volume: StateFlow<SystemVolumeSnapshot?>

    suspend fun start()
    suspend fun stop()
    suspend fun setVolumeDb(volumeDb: Float)
}

data class AudioRouteSnapshot(
    val id: String,
    val name: String,
    val isHeadphones: Boolean,
    val backendName: String? = null,
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
