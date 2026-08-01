package com.pararam2006.cmv.platform.linux

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.platform.MediaPlayerSnapshot
import com.pararam2006.cmv.platform.PlaybackStatus
import com.sun.jna.Memory
import com.sun.jna.Native
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxPlatformRuntimeTest {
    @Test
    fun parsesWpctlVolumeAndMuteState() {
        val state = WpctlOutputParser.parseVolume("Volume: 0.42 [MUTED]")

        assertEquals(42, state.currentVolume)
        assertEquals(100, state.maxVolume)
        assertTrue(state.isMuted)
    }

    @Test
    fun classifiesBluetoothHeadsetRoute() {
        val route = WpctlOutputParser.parseRoute(
            """
            |  * node.name = "bluez_output.11_22_33.a2dp-sink"
            |  * node.description = "Wireless Headset"
            |    device.form-factor = "headset"
            """.trimMargin(),
        )

        assertTrue(route.isHeadphones)
        assertEquals("bluez_output.11_22_33.a2dp-sink", route.id)
    }

    @Test
    fun doesNotClassifyBuiltInSpeakersAsHeadphones() {
        val route = WpctlOutputParser.parseRoute(
            """
            |  * node.name = "alsa_output.pci-0000_00_1f.3.analog-stereo"
            |  * node.description = "Built-in Audio Analog Stereo"
            |    device.profile.name = "analog-stereo"
            """.trimMargin(),
        )

        assertFalse(route.isHeadphones)
    }

    @Test
    fun classifiesPulseAudioPortsWithoutRelyingOnlyOnNames() {
        assertTrue(
            PulseAudioRouteClassifier.isHeadphones(
                portType = 3,
                hints = listOf("Built-in Audio", "Analog Output"),
            ),
        )
        assertFalse(
            PulseAudioRouteClassifier.isHeadphones(
                portType = 2,
                hints = listOf("Built-in Audio", "Speakers"),
            ),
        )
    }

    @Test
    fun jnaLayoutsMatch64BitLibpulseAbi() {
        if (Native.POINTER_SIZE != 8) return

        assertEquals(12, PulseSampleSpec().size())
        assertEquals(132, PulseChannelMap().size())
        assertEquals(132, PulseCVolume().size())
        assertEquals(40, PulseSinkPortInfo(Memory(40)).size())
        assertEquals(200, PulseServerInfo(Memory(200)).size())
        assertEquals(416, PulseSinkInfo(Memory(416)).size())
    }

    @Test
    fun liveLibpulseSubscriptionReceivesExternalVolumeEvent() = runBlocking {
        if (System.getenv("CMV_LIVE_PULSE_TEST") != "1") return@runBlocking

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = LinuxPulseAudioController()
        var originalVolume: Int? = null
        try {
            controller.start()
            val initial = assertNotNull(controller.volume.value)
            assertNotNull(controller.route.value)
            originalVolume = initial.currentVolume
            val target = if (initial.currentVolume < 100) {
                initial.currentVolume + 1
            } else {
                initial.currentVolume - 1
            }
            val scalar = String.format(Locale.US, "%.2f", target / 100.0)
            val process = ProcessBuilder(
                "wpctl",
                "set-volume",
                "@DEFAULT_AUDIO_SINK@",
                scalar,
                "--limit",
                "1.0",
            ).inheritIO().start()
            assertEquals(0, process.waitFor())

            val event = withTimeout(3_000) {
                controller.volume.first { it?.currentVolume == target }
            }
            assertEquals(target, event?.currentVolume)
        } finally {
            originalVolume?.let { runCatching { controller.setVolume(it) } }
            runCatching { controller.stop() }
            scope.cancel()
        }
    }

    @Test
    fun fallsBackToWpctlAndSharesOneBackendBetweenBothClients() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferred = FakeLinuxAudioBackend(startFailure = IllegalStateException("no libpulse"))
        val fallback = FakeLinuxAudioBackend()
        val controller = LinuxAudioController(
            scope = scope,
            preferredBackend = preferred,
            fallbackBackend = fallback,
        )
        try {
            controller.start()
            controller.start()
            assertEquals(1, preferred.startCount)
            assertEquals(1, fallback.startCount)
            assertEquals(40, controller.volume.value?.currentVolume)

            controller.setVolume(55)
            assertEquals(55, fallback.lastSetVolume)

            controller.stop()
            assertEquals(0, fallback.stopCount)
            controller.stop()
            assertEquals(1, fallback.stopCount)
        } finally {
            runCatching { controller.stop() }
            scope.cancel()
        }
    }

    @Test
    fun choosesLatestPlayingSelectedPlayer() {
        val older = player("vlc", "vlc.instance1", PlaybackStatus.PLAYING, sequence = 1)
        val newer = player("spotify", "spotify", PlaybackStatus.PLAYING, sequence = 2)

        val selected = selectActivePlayer(
            players = listOf(older, newer),
            selectedPackageNames = setOf("vlc", "spotify"),
            currentInstanceId = null,
        )

        assertEquals("spotify", selected?.app?.packageName)
    }

    @Test
    fun retainsPausedCurrentPlayer() {
        val paused = player("vlc", "vlc.instance1", PlaybackStatus.PAUSED, sequence = 1)
        val playing = player("spotify", "spotify", PlaybackStatus.PLAYING, sequence = 2)

        val selected = selectActivePlayer(
            players = listOf(paused, playing),
            selectedPackageNames = setOf("vlc", "spotify"),
            currentInstanceId = paused.instanceId,
        )

        assertEquals(paused, selected)
    }

    @Test
    fun ignoresUnselectedPlayers() {
        val playing = player("spotify", "spotify", PlaybackStatus.PLAYING, sequence = 1)

        val selected = selectActivePlayer(
            players = listOf(playing),
            selectedPackageNames = setOf("vlc"),
            currentInstanceId = null,
        )

        assertNull(selected)
    }

    @Test
    fun resolvesElectronMprisIdentityToItsDesktopEntry() {
        val desktopEntry = inferMprisDesktopEntryId(
            identity = "YandexMusic",
            desktopEntryIds = setOf("google-chrome", "yandexmusic"),
        )

        assertEquals("yandexmusic", desktopEntry)
    }

    @Test
    fun doesNotConfuseChromeIdentityWithYandexMusicDesktopEntry() {
        val desktopEntry = inferMprisDesktopEntryId(
            identity = "Chrome",
            desktopEntryIds = setOf("google-chrome", "yandexmusic"),
        )

        assertNull(desktopEntry)
    }

    @Test
    fun liveMprisDiscoverySeparatesYandexMusicFromChrome() = runBlocking {
        if (System.getenv("CMV_LIVE_MPRIS_TEST") != "1") return@runBlocking

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val monitor = LinuxMprisPlaybackMonitor(scope)
        try {
            monitor.start()
            val players = withTimeout(3_000) {
                monitor.players.first { snapshots ->
                    snapshots.any { it.app.label.contains("Яндекс", ignoreCase = true) }
                }
            }
            val yandexMusic = assertNotNull(
                players.firstOrNull {
                    it.app.label.contains("Яндекс", ignoreCase = true)
                },
            )
            assertEquals("yandexmusic", yandexMusic.app.packageName)
            assertTrue(players.none {
                it.app.packageName == yandexMusic.app.packageName &&
                    it.instanceId != yandexMusic.instanceId
            })
        } finally {
            runCatching { monitor.stop() }
            scope.cancel()
        }
    }

    private fun player(
        packageName: String,
        instanceId: String,
        status: PlaybackStatus,
        sequence: Long,
    ) = MediaPlayerSnapshot(
        app = AppInfo(
            label = packageName,
            iconUri = "",
            packageName = packageName,
            name = packageName,
        ),
        instanceId = instanceId,
        playbackStatus = status,
        trackTitle = "Track",
        trackArtist = "Artist",
        lastActivitySequence = sequence,
    )
}

private class FakeLinuxAudioBackend(
    private val startFailure: Exception? = null,
) : LinuxAudioBackend {
    override val backendName: String = "fake"
    override val volume: StateFlow<com.pararam2006.cmv.platform.SystemVolumeSnapshot?> =
        MutableStateFlow(
            com.pararam2006.cmv.platform.SystemVolumeSnapshot(
                currentVolume = 40,
                maxVolume = 100,
                isMuted = false,
            ),
        )
    override val route: StateFlow<com.pararam2006.cmv.platform.AudioRouteSnapshot?> =
        MutableStateFlow(
            com.pararam2006.cmv.platform.AudioRouteSnapshot(
                id = "fake",
                name = "Fake",
                isHeadphones = true,
            ),
        )

    var startCount = 0
    var stopCount = 0
    var lastSetVolume: Int? = null

    override suspend fun start() {
        startCount += 1
        startFailure?.let { throw it }
    }

    override suspend fun stop() {
        stopCount += 1
    }

    override suspend fun setVolume(volume: Int) {
        lastSetVolume = volume
    }
}
