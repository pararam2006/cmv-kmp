package com.pararam2006.cmv

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pararam2006.cmv.platform.AndroidSystemVolumeAdapter
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
