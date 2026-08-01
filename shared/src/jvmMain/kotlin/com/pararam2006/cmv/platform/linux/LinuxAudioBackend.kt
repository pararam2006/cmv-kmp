package com.pararam2006.cmv.platform.linux

import com.pararam2006.cmv.platform.AudioRouteMonitor
import com.pararam2006.cmv.platform.AudioRouteSnapshot
import com.pararam2006.cmv.platform.SystemVolumeController
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface LinuxAudioBackend {
    val backendName: String
    val volume: StateFlow<SystemVolumeSnapshot?>
    val route: StateFlow<AudioRouteSnapshot?>

    suspend fun start()
    suspend fun stop()
    suspend fun setVolume(volume: Int)
}

class LinuxAudioController internal constructor(
    private val scope: CoroutineScope,
    private val logger: (String) -> Unit = {},
    private val preferredBackend: LinuxAudioBackend = LinuxPulseAudioController(logger),
    private val fallbackBackend: LinuxAudioBackend = WpctlAudioController(scope, logger),
) : SystemVolumeController, AudioRouteMonitor {
    private val lifecycleMutex = Mutex()

    private val _volume = MutableStateFlow<SystemVolumeSnapshot?>(null)
    override val volume: StateFlow<SystemVolumeSnapshot?> = _volume.asStateFlow()

    private val _route = MutableStateFlow<AudioRouteSnapshot?>(null)
    override val route: StateFlow<AudioRouteSnapshot?> = _route.asStateFlow()

    @Volatile
    private var activeBackend: LinuxAudioBackend? = null
    private var volumeMirrorJob: Job? = null
    private var routeMirrorJob: Job? = null
    private var clients = 0

    override suspend fun start() = lifecycleMutex.withLock {
        if (clients > 0) {
            clients += 1
            return@withLock
        }

        check(isLinux()) { "System audio tracking is only available on Linux" }
        var lastFailure: Throwable? = null
        for (backend in listOf(preferredBackend, fallbackBackend).distinct()) {
            try {
                backend.start()
                activeBackend = backend
                clients = 1
                mirror(backend)
                logger("Linux audio monitor connected through ${backend.backendName}")
                return@withLock
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                lastFailure = failure
                logger("Linux audio backend ${backend.backendName} is unavailable: ${failure.message}")
                runCatching { backend.stop() }
            } catch (failure: LinkageError) {
                lastFailure = failure
                logger("Linux audio backend ${backend.backendName} cannot be loaded: ${failure.message}")
                runCatching { backend.stop() }
            }
        }
        throw IllegalStateException("No supported Linux audio backend is available", lastFailure)
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        clients = (clients - 1).coerceAtLeast(0)
        if (clients > 0) return@withLock

        volumeMirrorJob?.cancelAndJoin()
        routeMirrorJob?.cancelAndJoin()
        volumeMirrorJob = null
        routeMirrorJob = null
        val backend = activeBackend
        activeBackend = null
        runCatching { backend?.stop() }
            .onFailure { logger("Unable to stop Linux audio backend: ${it.message}") }
        _volume.value = null
        _route.value = null
    }

    override suspend fun setVolume(volume: Int) {
        val backend = activeBackend ?: error("Linux audio monitor is not running")
        backend.setVolume(volume.coerceIn(0, VIRTUAL_MAX_VOLUME))
    }

    private fun mirror(backend: LinuxAudioBackend) {
        _volume.value = backend.volume.value
        _route.value = backend.route.value
        volumeMirrorJob = scope.launch {
            backend.volume.collect { _volume.value = it }
        }
        routeMirrorJob = scope.launch {
            backend.route.collect { route ->
                _route.value = route
                logger(
                    "Linux audio route: " +
                        (route?.let { "${it.name} (${it.id}), headphones=${it.isHeadphones}" }
                            ?: "none"),
                )
            }
        }
    }

    private fun isLinux(): Boolean =
        System.getProperty("os.name").contains("linux", ignoreCase = true)

    companion object {
        const val VIRTUAL_MAX_VOLUME = 100
    }
}
