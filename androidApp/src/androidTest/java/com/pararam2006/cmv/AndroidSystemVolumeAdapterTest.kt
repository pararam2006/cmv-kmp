package com.pararam2006.cmv

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pararam2006.cmv.data.manager.VolumeLearningManagerImpl
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.platform.AndroidSystemVolumeAdapter
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSystemVolumeAdapterTest {

    @Test
    fun deviceCurveIsMonotonicAndMapsCurrentDbBackToCurrentIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val adapter = AndroidSystemVolumeAdapter(audioManager)

        val snapshot = adapter.snapshot()

        assertEquals(snapshot.maxVolume + 1, snapshot.volumeDbByStep.size)
        assertTrue(snapshot.volumeDbByStep.all(Float::isFinite))
        assertTrue(
            snapshot.volumeDbByStep.zipWithNext().all { (lower, upper) ->
                lower <= upper
            },
        )

        val mappedIndex = snapshot.nativeVolumeForDb(snapshot.currentVolumeDb)
        val nearestDistance = snapshot.volumeDbByStep.minOf { candidate ->
            abs(candidate - snapshot.currentVolumeDb)
        }
        assertEquals(
            nearestDistance,
            abs(snapshot.dbForNativeVolume(mappedIndex) - snapshot.currentVolumeDb),
            0.0001f,
        )
    }

    @Test
    fun samsungSmallDbOffsetsDoNotJumpToScaleEdges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val adapter = AndroidSystemVolumeAdapter(audioManager)
        val original = adapter.snapshot()
        assumeTrue("Samsung fine scale is not available", original.maxVolume >= 100)

        try {
            adapter.setVolumeDb(TEST_BASELINE_DB, 0)
            SystemClock.sleep(VOLUME_SETTLE_MS)
            val baseline = adapter.snapshot()

            adapter.setVolumeDb(baseline.currentVolumeDb + TEST_OFFSET_DB, 0)
            SystemClock.sleep(VOLUME_SETTLE_MS)
            val louder = adapter.snapshot()

            adapter.setVolumeDb(baseline.currentVolumeDb, 0)
            SystemClock.sleep(VOLUME_SETTLE_MS)
            adapter.setVolumeDb(baseline.currentVolumeDb - TEST_OFFSET_DB, 0)
            SystemClock.sleep(VOLUME_SETTLE_MS)
            val quieter = adapter.snapshot()

            println(
                "CMV_VOLUME_TEST baseline=${baseline.currentVolume}/${baseline.maxVolume} " +
                    "(${baseline.currentVolumeDb}dB), louder=${louder.currentVolume} " +
                    "(${louder.currentVolumeDb}dB), quieter=${quieter.currentVolume} " +
                    "(${quieter.currentVolumeDb}dB)",
            )
            assertTrue("+0.5 dB did not raise the native level", louder.currentVolume > baseline.currentVolume)
            assertTrue("-0.5 dB did not lower the native level", quieter.currentVolume < baseline.currentVolume)
            assertTrue("+0.5 dB jumped to maximum", louder.currentVolume < louder.maxVolume)
            assertTrue("-0.5 dB jumped to minimum", quieter.currentVolume > 0)
        } finally {
            adapter.setVolumeDb(original.currentVolumeDb, 0)
            SystemClock.sleep(VOLUME_SETTLE_MS)
        }
    }

    @Test
    fun highOffsetResetsToBaseAfterTransientSessionDetach() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val adapter = AndroidSystemVolumeAdapter(audioManager)
        val original = adapter.snapshot()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        assumeTrue("Samsung fine scale is not available", original.maxVolume >= 100)

        try {
            adapter.setVolumeDb(TEST_RESET_BASELINE_DB, 0)
            delay(VOLUME_SETTLE_MS)
            val baseline = adapter.snapshot()
            val manager = VolumeLearningManagerImpl(
                saveTrackVolumeUseCase = SaveTrackVolumeUseCase(EmptyTrackVolumeRepository()),
                appModeFlow = MutableStateFlow(AppMode.LEARNING),
                learningTimeSeconds = { 15 },
                volumeJumpProtectionEnabled = { true },
                scope = managerScope,
                nowMillis = { SystemClock.elapsedRealtime() },
            )
            delay(EVENT_SETTLE_MS)

            manager.onHeadsetStateChanged(true)
            manager.onAudioFocusChanged(true)
            manager.onPlaybackStateChanged(true)
            val boostCommand = async {
                withTimeout(TEST_TIMEOUT_MS) { manager.volumeCommands.first() }
            }
            manager.onTrackChanged(
                title = "Quiet track",
                artist = "Artist",
                volumeOffset = TEST_HIGH_OFFSET_DB,
                offsetModel = VolumeOffsetModel.DECIBEL,
                systemVolume = baseline,
                trackGeneration = 1,
            )

            val boost = boostCommand.await()
            assertEquals(baseline.currentVolumeDb + TEST_HIGH_OFFSET_DB, boost.targetVolumeDb, 0.001f)
            adapter.setVolumeDb(boost.targetVolumeDb, 0)
            delay(VOLUME_SETTLE_MS)
            val boosted = adapter.snapshot()
            assertTrue(boosted.currentVolume > baseline.currentVolume)

            manager.onVolumeChanged(boosted)
            manager.onPlaybackStateChanged(false)
            manager.onSessionDetached()
            withTimeout(TEST_TIMEOUT_MS) {
                manager.debugState.first {
                    it.currentTrackTitle == null &&
                        it.previousTrackOffsetDb >= TEST_HIGH_OFFSET_DB
                }
            }

            manager.onTrackChanged(
                title = "Neutral next track",
                artist = "Artist",
                volumeOffset = 0f,
                offsetModel = VolumeOffsetModel.DECIBEL,
                systemVolume = boosted,
                trackGeneration = 2,
            )
            val pendingState = withTimeout(TEST_TIMEOUT_MS) {
                manager.debugState.first { it.currentTrackTitle == "Neutral next track" }
            }
            assertTrue(pendingState.volumeJumpProtectionApplied)
            assertTrue(pendingState.volumeJumpProtectionPending)

            val resetCommand = async {
                withTimeout(TEST_TIMEOUT_MS) { manager.volumeCommands.first() }
            }
            manager.onPlaybackStateChanged(true)
            val reset = resetCommand.await()
            assertEquals(baseline.currentVolumeDb, reset.targetVolumeDb, 0.001f)

            adapter.setVolumeDb(reset.targetVolumeDb, 0)
            delay(VOLUME_SETTLE_MS)
            val restored = adapter.snapshot()
            println(
                "CMV_JUMP_PROTECTION_TEST baseline=${baseline.currentVolume}/${baseline.maxVolume} " +
                    "(${baseline.currentVolumeDb}dB), boosted=${boosted.currentVolume} " +
                    "(${boosted.currentVolumeDb}dB), restored=${restored.currentVolume} " +
                    "(${restored.currentVolumeDb}dB)",
            )
            assertEquals(baseline.currentVolume, restored.currentVolume)
            assertFalse(manager.debugState.value.volumeJumpProtectionPending)
        } finally {
            managerScope.cancel()
            adapter.setVolumeDb(original.currentVolumeDb, 0)
            delay(VOLUME_SETTLE_MS)
        }
    }

    private class EmptyTrackVolumeRepository : TrackVolumeRepository {
        override fun getAllTrackVolumes(): Flow<List<TrackVolume>> =
            MutableStateFlow(emptyList())

        override suspend fun getTrackVolume(title: String, artist: String?): TrackVolume? = null
        override suspend fun getTrackVolumeById(id: Int): TrackVolume? = null
        override suspend fun saveTrackVolume(trackVolume: TrackVolume) = Unit
        override suspend fun deleteTrackVolume(trackVolume: TrackVolume) = Unit
        override suspend fun deleteTrackVolumeById(id: Int) = Unit
    }

    private companion object {
        const val TEST_BASELINE_DB = -24f
        const val TEST_OFFSET_DB = 0.5f
        const val TEST_RESET_BASELINE_DB = -30f
        const val TEST_HIGH_OFFSET_DB = 9f
        const val VOLUME_SETTLE_MS = 400L
        const val EVENT_SETTLE_MS = 100L
        const val TEST_TIMEOUT_MS = 5_000L
    }
}
