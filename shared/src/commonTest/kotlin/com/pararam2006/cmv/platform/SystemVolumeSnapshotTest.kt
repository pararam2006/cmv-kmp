package com.pararam2006.cmv.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemVolumeSnapshotTest {

    @Test
    fun subdividedCurveInterpolatesAmplitudeAndKeepsDbAnchors() {
        val curve = subdivideVolumeDbCurve(
            volumeDbByStep = listOf(-200f, -42f, -39f),
            subdivisionsPerStep = 10,
            muteVolumeDb = -200f,
        )

        assertEquals(21, curve.size)
        assertEquals(-200f, curve[0])
        assertEquals(-42f, curve[10])
        assertEquals(-39f, curve[20])
        assertEquals(-62f, curve[1], absoluteTolerance = 0.05f)
        assertEquals(-40.37f, curve[15], absoluteTolerance = 0.05f)
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
    fun repeatedDbValuesPreferTheLevelClosestToCurrentVolume() {
        val repeatedCurve = SystemVolumeSnapshot(
            currentVolume = 3,
            maxVolume = 5,
            isMuted = false,
            volumeDbByStep = listOf(-80f, -20f, -20f, -20f, -10f, 0f),
        )

        assertEquals(3, repeatedCurve.nativeVolumeForDb(-20f))
    }

    @Test
    fun nonFiniteTargetKeepsCurrentVolumeInsteadOfSelectingAnEdge() {
        assertEquals(snapshot.currentVolume, snapshot.nativeVolumeForDb(Float.NaN))
        assertEquals(snapshot.currentVolume, snapshot.nativeVolumeForDb(Float.POSITIVE_INFINITY))
        assertEquals(snapshot.currentVolume, snapshot.nativeVolumeForDb(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun samsungAmplitudeTableMapsSmallDbOffsetsToNearbyFineLevels() {
        val publicAmplitudes = listOf(0f, 0.007943f, 0.01122f, 0.015849f)
        val fineAmplitudes = buildList {
            publicAmplitudes.zipWithNext().forEach { (lower, upper) ->
                repeat(10) { subdivision ->
                    add(lower + (upper - lower) * subdivision / 10f)
                }
            }
            add(publicAmplitudes.last())
        }
        val curve = amplitudeVolumeCurveToDb(fineAmplitudes)!!
        val fineSnapshot = SystemVolumeSnapshot(
            currentVolume = 20,
            maxVolume = curve.lastIndex,
            isMuted = false,
            volumeDbByStep = curve,
        )

        val louder = fineSnapshot.nativeVolumeForDb(fineSnapshot.currentVolumeDb + 0.5f)
        val quieter = fineSnapshot.nativeVolumeForDb(fineSnapshot.currentVolumeDb - 0.5f)

        assertEquals(21, louder)
        assertEquals(18, quieter)
    }

    @Test
    fun rejectsInvalidAmplitudeTable() {
        assertEquals(null, amplitudeVolumeCurveToDb(listOf(0f, 0.5f, 0.4f)))
        assertEquals(null, amplitudeVolumeCurveToDb(listOf(0f, Float.NaN, 1f)))
    }

    @Test
    fun convertsSamsungLinearGainReportedAsDbIntoActualDb() {
        val normalized = normalizeReportedVolumeDbCurve(
            listOf(0f, 0.007943f, 0.01122f, 0.015849f, 1f),
        )

        assertEquals(-200f, normalized[0])
        assertEquals(-42f, normalized[1], absoluteTolerance = 0.01f)
        assertEquals(-39f, normalized[2], absoluteTolerance = 0.01f)
        assertEquals(-36f, normalized[3], absoluteTolerance = 0.01f)
        assertEquals(0f, normalized[4], absoluteTolerance = 0.01f)
    }

    @Test
    fun keepsRealDbCurveUnchanged() {
        val reportedDb = listOf(Float.NEGATIVE_INFINITY, -42f, -39f, -36f, 1f)

        assertEquals(
            listOf(-200f, -42f, -39f, -36f, 1f),
            normalizeReportedVolumeDbCurve(reportedDb),
        )
    }

    @Test
    fun convertsLegacyRatioUsingTheProvidedDeviceCurve() {
        // Legacy formula from native base 2 with ratio 1.5 targets native step 4.
        assertEquals(12f, snapshot.legacyRatioToOffsetDb(baseNativeVolume = 2, ratio = 1.5f))
    }
}
