package com.pararam2006.cmv.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemVolumeSnapshotTest {

    @Test
    fun subdividedCurveKeepsAnchorsAndAddsFineSteps() {
        val curve = subdivideVolumeDbCurve(
            volumeDbByStep = listOf(-60f, -30f, 0f),
            subdivisionsPerStep = 10,
        )

        assertEquals(21, curve.size)
        assertEquals(-60f, curve[0])
        assertEquals(-30f, curve[10])
        assertEquals(0f, curve[20])
        assertEquals(-45f, curve[5])
        assertEquals(-15f, curve[15])
    }

    @Test
    fun samsungFineVolumeUsesCeilingForThePublicLevel() {
        assertEquals(0, 0.toCoarseVolume(subdivisionsPerStep = 10))
        assertEquals(1, 1.toCoarseVolume(subdivisionsPerStep = 10))
        assertEquals(1, 10.toCoarseVolume(subdivisionsPerStep = 10))
        assertEquals(2, 11.toCoarseVolume(subdivisionsPerStep = 10))
        assertEquals(15, 150.toCoarseVolume(subdivisionsPerStep = 10))
    }

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
