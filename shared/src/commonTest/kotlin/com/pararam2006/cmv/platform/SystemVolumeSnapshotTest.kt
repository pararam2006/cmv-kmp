package com.pararam2006.cmv.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemVolumeSnapshotTest {
    @Test
    fun muteSentinelDoesNotCreateAnInfiniteEchoTolerance() {
        val mutedSnapshot = SystemVolumeSnapshot(
            currentVolume = 0,
            maxVolume = 3,
            isMuted = true,
            volumeDbByStep = listOf(-200f, -36f, -30f, -24f),
        )

        assertEquals(0.1f, mutedSnapshot.volumeStepDb)
    }

    private val snapshot = SystemVolumeSnapshot(
        currentVolume = 2,
        maxVolume = 4,
        isMuted = false,
        volumeDbByStep = listOf(-80f, -30f, -12f, -5f, 0f),
    )

    @Test
    fun mapsDbToNearestNativeStepAndClampsTargets() {
        assertEquals(2, snapshot.nativeVolumeForDb(-10f))
        assertEquals(-80f, snapshot.clampDb(-100f))
        assertEquals(0f, snapshot.clampDb(6f))
    }

    @Test
    fun convertsLegacyRatioUsingTheProvidedDeviceCurve() {
        // Legacy formula from native base 2 with ratio 1.5 targets native step 4.
        assertEquals(12f, snapshot.legacyRatioToOffsetDb(baseNativeVolume = 2, ratio = 1.5f))
    }
}
