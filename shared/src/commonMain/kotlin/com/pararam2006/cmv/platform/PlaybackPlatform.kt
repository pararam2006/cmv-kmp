package com.pararam2006.cmv.platform

import com.pararam2006.cmv.domain.model.AppInfo
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
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

    fun nativeVolumeForDb(volumeDb: Float): Int {
        if (!volumeDb.isFinite()) return currentVolume.coerceIn(0, maxVolume)

        val targetDb = clampDb(volumeDb)
        return volumeDbByStep.indices.minWithOrNull(
            compareBy<Int> { abs(volumeDbByStep[it] - targetDb) }
                // OEM curves can contain repeated dB values. Choosing the first
                // equal value would turn a harmless correction into a jump to
                // the edge of the native scale.
                .thenBy { abs(it - currentVolume) },
        ) ?: currentVolume.coerceIn(0, maxVolume)
    }

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

internal fun subdivideVolumeDbCurve(
    volumeDbByStep: List<Float>,
    subdivisionsPerStep: Int,
    muteVolumeDb: Float? = null,
): List<Float> {
    require(volumeDbByStep.isNotEmpty())
    require(subdivisionsPerStep > 0)
    if (volumeDbByStep.size == 1 || subdivisionsPerStep == 1) return volumeDbByStep

    val amplitudes = volumeDbByStep.map { volumeDb ->
        if (muteVolumeDb != null && volumeDb <= muteVolumeDb) {
            0.0
        } else {
            10.0.pow(volumeDb / 20.0)
        }
    }
    val maxSubdividedIndex = (volumeDbByStep.lastIndex * subdivisionsPerStep)
    return List(maxSubdividedIndex + 1) { subdividedIndex ->
        val lowerIndex = subdividedIndex / subdivisionsPerStep
        val subdivision = subdividedIndex % subdivisionsPerStep
        if (lowerIndex == volumeDbByStep.lastIndex || subdivision == 0) {
            volumeDbByStep[lowerIndex]
        } else {
            val fraction = subdivision.toDouble() / subdivisionsPerStep
            val lowerAmplitude = amplitudes[lowerIndex]
            val amplitude = lowerAmplitude +
                (amplitudes[lowerIndex + 1] - lowerAmplitude) * fraction
            if (amplitude <= 0.0) {
                muteVolumeDb ?: volumeDbByStep[lowerIndex]
            } else {
                (20.0 * log10(amplitude)).toFloat().let { interpolatedDb ->
                    muteVolumeDb?.let { maxOf(it, interpolatedDb) } ?: interpolatedDb
                }
            }
        }
    }
}

internal fun amplitudeVolumeCurveToDb(
    amplitudes: List<Float>,
    maxVolumeDb: Float = 0f,
    muteVolumeDb: Float = -200f,
): List<Float>? {
    if (amplitudes.isEmpty() || !maxVolumeDb.isFinite() || !muteVolumeDb.isFinite()) return null
    if (amplitudes.any { !it.isFinite() || it < 0f }) return null
    if (amplitudes.zipWithNext().any { (lower, upper) -> lower > upper }) return null

    val maxAmplitude = amplitudes.last()
    if (maxAmplitude <= 0f) return null

    return amplitudes.map { amplitude ->
        if (amplitude <= 0f) {
            muteVolumeDb
        } else {
            (20.0 * log10(amplitude.toDouble() / maxAmplitude) + maxVolumeDb)
                .toFloat()
                .coerceAtLeast(muteVolumeDb)
        }
    }
}

internal fun normalizeReportedVolumeDbCurve(
    reportedValues: List<Float>,
    muteVolumeDb: Float = -200f,
): List<Float> {
    require(reportedValues.isNotEmpty())

    val looksLikeLinearAmplitude = reportedValues.all(Float::isFinite) &&
        reportedValues.all { it in 0f..MAX_LINEAR_AMPLITUDE } &&
        reportedValues.zipWithNext().all { (lower, upper) -> lower <= upper } &&
        reportedValues.first() <= MAX_MUTED_AMPLITUDE &&
        reportedValues.last() >= MIN_NORMALIZED_MAX_AMPLITUDE

    if (looksLikeLinearAmplitude) {
        amplitudeVolumeCurveToDb(
            amplitudes = reportedValues,
            maxVolumeDb = 0f,
            muteVolumeDb = muteVolumeDb,
        )?.let { return it }
    }

    var previousDb = muteVolumeDb
    return reportedValues.map { reportedValue ->
        val finiteDb = if (reportedValue.isFinite()) reportedValue else muteVolumeDb
        finiteDb.coerceAtLeast(previousDb).also { previousDb = it }
    }
}

internal fun Int.toCoarseVolume(subdivisionsPerStep: Int): Int {
    require(this >= 0)
    require(subdivisionsPerStep > 0)
    return if (this == 0) 0 else (this + subdivisionsPerStep - 1) / subdivisionsPerStep
}

private const val MAX_LINEAR_AMPLITUDE = 1.5f
private const val MAX_MUTED_AMPLITUDE = 0.001f
private const val MIN_NORMALIZED_MAX_AMPLITUDE = 0.5f

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
