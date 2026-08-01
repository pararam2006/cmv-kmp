package com.pararam2006.cmv.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumeStateTest {
    @Test
    fun activePlayingTimeIncludesCurrentChunk() {
        val state = VolumeState(
            accumulatedPlayingTimeMs = 2_000,
            currentPlayChunkStartMs = 5_000,
            isPlaying = true,
        )

        assertEquals(6_000, state.activePlayingTimeMs(now = 9_000))
    }

    @Test
    fun savingThresholdRequiresPlaybackAndManualChangeStability() {
        val state = VolumeState(
            accumulatedPlayingTimeMs = 10_000,
            lastManualVolumeChangeTimeMs = 5_000,
        )

        assertFalse(state.isSavingThresholdReached(now = 14_999, thresholdMs = 10_000))
        assertTrue(state.isSavingThresholdReached(now = 15_000, thresholdMs = 10_000))
    }
}
