package com.pararam2006.cmv.domain.manager

data class VolumeState(
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val trackStartTimeMs: Long = 0,
    val accumulatedPlayingTimeMs: Long = 0,
    val currentPlayChunkStartMs: Long = 0,
    val isPlaying: Boolean = false,
    val baseVolume: Int = -1,
    val expectedProgrammaticVolume: Int = -1,
    val currentLearnedOffset: Float = 1f,
    val maxVolume: Int = 15,
    val hasLearnedOffsetChanged: Boolean = false,
    val lastManualVolumeChangeTimeMs: Long = 0,
    val isHeadsetConnected: Boolean = false,
    val hasAudioFocus: Boolean = true,
    val activeSessionPackageName: String? = null,
    val trackGeneration: Long = 0,
)

fun VolumeState.activePlayingTimeMs(now: Long): Long {
    val currentChunk = if (isPlaying && currentPlayChunkStartMs > 0) {
        now - currentPlayChunkStartMs
    } else 0L
    return accumulatedPlayingTimeMs + currentChunk
}

fun VolumeState.timeSinceLastManualChangeMs(now: Long): Long {
    return if (lastManualVolumeChangeTimeMs > 0) now - lastManualVolumeChangeTimeMs else 0L
}

fun VolumeState.isSavingThresholdReached(now: Long, thresholdMs: Long): Boolean {
    return activePlayingTimeMs(now) >= thresholdMs &&
            timeSinceLastManualChangeMs(now) >= thresholdMs
}