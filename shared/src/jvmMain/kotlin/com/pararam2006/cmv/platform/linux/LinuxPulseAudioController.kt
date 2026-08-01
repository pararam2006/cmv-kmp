package com.pararam2006.cmv.platform.linux

import com.pararam2006.cmv.platform.AudioRouteSnapshot
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Event-driven Linux volume and route backend.
 *
 * PipeWire exposes its graph through pipewire-pulse, so the same stable libpulse
 * client API works with both a native PulseAudio daemon and the usual modern
 * PipeWire desktop stack. All callbacks are delivered by pa_threaded_mainloop.
 */
internal class LinuxPulseAudioController(
    private val logger: (String) -> Unit = {},
    private val libraryProvider: () -> PulseAudioLibrary = { PulseAudioLibrary.load() },
) : LinuxAudioBackend {
    override val backendName: String = "libpulse events"

    private val _volume = MutableStateFlow<SystemVolumeSnapshot?>(null)
    override val volume: StateFlow<SystemVolumeSnapshot?> = _volume.asStateFlow()

    private val _route = MutableStateFlow<AudioRouteSnapshot?>(null)
    override val route: StateFlow<AudioRouteSnapshot?> = _route.asStateFlow()

    @Volatile
    private var library: PulseAudioLibrary? = null

    @Volatile
    private var mainloop: Pointer? = null

    @Volatile
    private var context: Pointer? = null

    @Volatile
    private var mainloopStarted = false

    @Volatile
    private var contextState = PA_CONTEXT_UNCONNECTED

    @Volatile
    private var defaultSinkName: String? = null

    @Volatile
    private var defaultSinkIndex: Int? = null

    @Volatile
    private var defaultSinkChannels = 2

    @Volatile
    private var connectionLatch: CountDownLatch? = null

    @Volatile
    private var initialSnapshotLatch: CountDownLatch? = null

    private val pendingSuccessCallbacks = ConcurrentHashMap.newKeySet<PulseContextSuccessCallback>()

    private val stateCallback = PulseContextNotifyCallback { callbackContext, _ ->
        val lib = library
        if (lib != null && callbackContext != null) {
            val newState = runCatching { lib.pa_context_get_state(callbackContext) }
                .getOrDefault(PA_CONTEXT_FAILED)
            contextState = newState
            if (newState == PA_CONTEXT_READY ||
                newState == PA_CONTEXT_FAILED ||
                newState == PA_CONTEXT_TERMINATED
            ) {
                connectionLatch?.countDown()
            }
            if (newState == PA_CONTEXT_FAILED || newState == PA_CONTEXT_TERMINATED) {
                _volume.value = null
                _route.value = null
                if (mainloopStarted) {
                    logger("libpulse connection ended: ${pulseError(lib, callbackContext)}")
                }
            }
        }
    }

    private val serverInfoCallback = PulseServerInfoCallback { callbackContext, infoPointer, _ ->
        val lib = library
        if (lib != null && callbackContext != null && infoPointer != null) {
            runCatching {
                val info = PulseServerInfo(infoPointer)
                val sinkName = info.defaultSinkName.stringValue()
                    ?: error("PulseAudio server has no default sink")
                val sinkChanged = sinkName != defaultSinkName
                defaultSinkName = sinkName
                if (sinkChanged) {
                    defaultSinkIndex = null
                    _volume.value = null
                    _route.value = null
                }
                requestSinkInfoLocked(lib, callbackContext, sinkName)
            }.onFailure(::handleCallbackFailure)
        } else {
            initialSnapshotLatch?.countDown()
        }
    }

    private val sinkInfoCallback = PulseSinkInfoCallback { _, infoPointer, endOfList, _ ->
        if (infoPointer != null && endOfList == 0) {
            runCatching {
                val info = PulseSinkInfo(infoPointer)
                val snapshot = PulseSinkSnapshot.from(info)
                if (snapshot.sinkName == defaultSinkName) {
                    defaultSinkIndex = snapshot.sinkIndex
                    defaultSinkChannels = snapshot.channels.coerceIn(1, PA_CHANNELS_MAX)
                    _volume.value = snapshot.volume
                    _route.value = snapshot.route
                    initialSnapshotLatch?.countDown()
                }
            }.onFailure(::handleCallbackFailure)
        } else if (endOfList != 0) {
            initialSnapshotLatch?.countDown()
        }
    }

    private val subscribeCallback = PulseSubscribeCallback { callbackContext, eventType, eventIndex, _ ->
        val lib = library
        if (lib != null && callbackContext != null) {
            runCatching {
                when (eventType and PA_SUBSCRIPTION_EVENT_FACILITY_MASK) {
                    PA_SUBSCRIPTION_EVENT_SERVER -> requestServerInfoLocked(lib, callbackContext)
                    PA_SUBSCRIPTION_EVENT_CARD -> requestCurrentSinkLocked(lib, callbackContext)
                    PA_SUBSCRIPTION_EVENT_SINK -> {
                        val currentIndex = defaultSinkIndex
                        if (currentIndex == null || currentIndex == eventIndex) {
                            requestCurrentSinkLocked(lib, callbackContext)
                        }
                    }
                }
            }.onFailure(::handleCallbackFailure)
        }
    }

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (mainloop != null) return@withContext
        check(System.getProperty("os.name").contains("linux", ignoreCase = true)) {
            "libpulse audio tracking is only available on Linux"
        }

        val lib = libraryProvider()
        val newMainloop = lib.pa_threaded_mainloop_new()
            ?: error("Unable to create the libpulse threaded mainloop")
        val mainloopApi = lib.pa_threaded_mainloop_get_api(newMainloop)
            ?: run {
                lib.pa_threaded_mainloop_free(newMainloop)
                error("Unable to get the libpulse mainloop API")
            }
        val newContext = lib.pa_context_new(mainloopApi, APPLICATION_NAME)
            ?: run {
                lib.pa_threaded_mainloop_free(newMainloop)
                error("Unable to create a libpulse context")
            }

        library = lib
        mainloop = newMainloop
        context = newContext
        contextState = PA_CONTEXT_UNCONNECTED
        val connected = CountDownLatch(1)
        connectionLatch = connected

        try {
            lib.pa_context_set_state_callback(newContext, stateCallback, null)
            check(lib.pa_context_connect(newContext, null, PA_CONTEXT_NOFLAGS, null) >= 0) {
                "Unable to connect to the PulseAudio-compatible server: ${pulseError(lib, newContext)}"
            }
            check(lib.pa_threaded_mainloop_start(newMainloop) >= 0) {
                "Unable to start the libpulse threaded mainloop"
            }
            mainloopStarted = true

            check(connected.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out connecting to the PulseAudio-compatible server"
            }
            check(contextState == PA_CONTEXT_READY) {
                "Unable to connect to the PulseAudio-compatible server: ${pulseError(lib, newContext)}"
            }

            val initialSnapshot = CountDownLatch(1)
            initialSnapshotLatch = initialSnapshot
            withMainloopLock(lib, newMainloop) {
                lib.pa_context_set_subscribe_callback(newContext, subscribeCallback, null)
                val subscribeOperation = lib.pa_context_subscribe(
                    newContext,
                    PA_SUBSCRIPTION_MASK_SINK or PA_SUBSCRIPTION_MASK_SERVER or PA_SUBSCRIPTION_MASK_CARD,
                    null,
                    null,
                ) ?: error("Unable to subscribe to libpulse events: ${pulseError(lib, newContext)}")
                lib.pa_operation_unref(subscribeOperation)
                requestServerInfoLocked(lib, newContext)
            }
            check(initialSnapshot.await(INITIAL_SNAPSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out reading the default audio sink from libpulse"
            }
            check(_volume.value != null && _route.value != null) {
                "libpulse did not return a usable default audio sink"
            }
        } catch (failure: Throwable) {
            closeNative()
            throw failure
        } finally {
            connectionLatch = null
            initialSnapshotLatch = null
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        closeNative()
    }

    override suspend fun setVolume(volume: Int) = withContext(Dispatchers.IO) {
        val lib = library ?: error("libpulse backend is not running")
        val loop = mainloop ?: error("libpulse backend is not running")
        val currentContext = context ?: error("libpulse backend is not running")
        val sinkName = defaultSinkName ?: error("libpulse has no default sink")
        check(contextState == PA_CONTEXT_READY) { "libpulse connection is not ready" }

        val targetPercent = volume.coerceIn(0, LinuxAudioController.VIRTUAL_MAX_VOLUME)
        val targetNativeVolume = (
            targetPercent.toDouble() * PA_VOLUME_NORM / LinuxAudioController.VIRTUAL_MAX_VOLUME
        ).roundToInt()
        val channelVolume = PulseCVolume()
        check(lib.pa_cvolume_set(channelVolume, defaultSinkChannels, targetNativeVolume) != null) {
            "Unable to prepare a libpulse channel volume"
        }

        val result = AtomicReference<Boolean?>(null)
        val completed = CountDownLatch(1)
        lateinit var callback: PulseContextSuccessCallback
        callback = PulseContextSuccessCallback { _, success, _ ->
            result.set(success != 0)
            completed.countDown()
            pendingSuccessCallbacks.remove(callback)
        }
        pendingSuccessCallbacks.add(callback)

        try {
            withMainloopLock(lib, loop) {
                val operation = lib.pa_context_set_sink_volume_by_name(
                    currentContext,
                    sinkName,
                    channelVolume,
                    callback,
                    null,
                ) ?: error("Unable to set sink volume: ${pulseError(lib, currentContext)}")
                lib.pa_operation_unref(operation)
            }
        } catch (failure: Throwable) {
            pendingSuccessCallbacks.remove(callback)
            throw failure
        }

        check(completed.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Timed out setting the default sink volume through libpulse"
        }
        check(result.get() == true) {
            "Unable to set the default sink volume: ${pulseError(lib, currentContext)}"
        }
        _volume.value = _volume.value?.copy(currentVolume = targetPercent)
    }

    private fun requestServerInfoLocked(lib: PulseAudioLibrary, currentContext: Pointer) {
        val operation = lib.pa_context_get_server_info(currentContext, serverInfoCallback, null)
            ?: error("Unable to read libpulse server info: ${pulseError(lib, currentContext)}")
        lib.pa_operation_unref(operation)
    }

    private fun requestCurrentSinkLocked(lib: PulseAudioLibrary, currentContext: Pointer) {
        defaultSinkName?.let { requestSinkInfoLocked(lib, currentContext, it) }
            ?: requestServerInfoLocked(lib, currentContext)
    }

    private fun requestSinkInfoLocked(
        lib: PulseAudioLibrary,
        currentContext: Pointer,
        sinkName: String,
    ) {
        val operation = lib.pa_context_get_sink_info_by_name(
            currentContext,
            sinkName,
            sinkInfoCallback,
            null,
        ) ?: error("Unable to read default sink '$sinkName': ${pulseError(lib, currentContext)}")
        lib.pa_operation_unref(operation)
    }

    private fun closeNative() {
        val lib = library ?: return clearSnapshots()
        val loop = mainloop
        val currentContext = context
        val started = mainloopStarted

        if (loop != null && currentContext != null) {
            if (started) {
                lib.pa_threaded_mainloop_lock(loop)
                try {
                    lib.pa_context_set_subscribe_callback(currentContext, null, null)
                    lib.pa_context_set_state_callback(currentContext, null, null)
                    lib.pa_context_disconnect(currentContext)
                    lib.pa_context_unref(currentContext)
                } finally {
                    lib.pa_threaded_mainloop_unlock(loop)
                }
                lib.pa_threaded_mainloop_stop(loop)
            } else {
                lib.pa_context_set_state_callback(currentContext, null, null)
                lib.pa_context_disconnect(currentContext)
                lib.pa_context_unref(currentContext)
            }
            lib.pa_threaded_mainloop_free(loop)
        } else {
            currentContext?.let(lib::pa_context_unref)
            loop?.let(lib::pa_threaded_mainloop_free)
        }

        mainloopStarted = false
        context = null
        mainloop = null
        library = null
        contextState = PA_CONTEXT_UNCONNECTED
        defaultSinkName = null
        defaultSinkIndex = null
        defaultSinkChannels = 2
        pendingSuccessCallbacks.clear()
        clearSnapshots()
    }

    private fun clearSnapshots() {
        _volume.value = null
        _route.value = null
    }

    private fun handleCallbackFailure(failure: Throwable) {
        logger("libpulse event handling failed: ${failure.message}")
        initialSnapshotLatch?.countDown()
    }

    private inline fun withMainloopLock(
        lib: PulseAudioLibrary,
        loop: Pointer,
        action: () -> Unit,
    ) {
        lib.pa_threaded_mainloop_lock(loop)
        try {
            action()
        } finally {
            lib.pa_threaded_mainloop_unlock(loop)
        }
    }

    private companion object {
        const val APPLICATION_NAME = "Custom Music Volume"
        const val CONNECTION_TIMEOUT_SECONDS = 5L
        const val INITIAL_SNAPSHOT_TIMEOUT_SECONDS = 5L
        const val OPERATION_TIMEOUT_SECONDS = 3L

        const val PA_CONTEXT_UNCONNECTED = 0
        const val PA_CONTEXT_READY = 4
        const val PA_CONTEXT_FAILED = 5
        const val PA_CONTEXT_TERMINATED = 6
        const val PA_CONTEXT_NOFLAGS = 0

        const val PA_SUBSCRIPTION_MASK_SINK = 0x0001
        const val PA_SUBSCRIPTION_MASK_SERVER = 0x0080
        const val PA_SUBSCRIPTION_MASK_CARD = 0x0200
        const val PA_SUBSCRIPTION_EVENT_SINK = 0x0000
        const val PA_SUBSCRIPTION_EVENT_SERVER = 0x0007
        const val PA_SUBSCRIPTION_EVENT_CARD = 0x0009
        const val PA_SUBSCRIPTION_EVENT_FACILITY_MASK = 0x000F

        const val PA_CHANNELS_MAX = 32
        const val PA_VOLUME_NORM = 0x10000
    }
}

internal data class PulseSinkSnapshot(
    val sinkName: String,
    val sinkIndex: Int,
    val channels: Int,
    val volume: SystemVolumeSnapshot,
    val route: AudioRouteSnapshot,
) {
    companion object {
        fun from(info: PulseSinkInfo): PulseSinkSnapshot {
            val sinkName = info.name.stringValue() ?: error("libpulse sink has no name")
            val description = info.description.stringValue() ?: sinkName
            val channelCount = info.volume.channels.toInt() and 0xFF
            check(channelCount in 1..PulseCVolume.CHANNELS_MAX) {
                "libpulse returned an invalid channel count: $channelCount"
            }
            val nativeAverage = info.volume.values
                .take(channelCount)
                .map(Integer::toUnsignedLong)
                .average()
            val volumePercent = (
                nativeAverage * LinuxAudioController.VIRTUAL_MAX_VOLUME / PulseCVolume.VOLUME_NORM
            ).roundToInt().coerceIn(0, LinuxAudioController.VIRTUAL_MAX_VOLUME)

            val activePort = info.activePort?.let(::PulseSinkPortInfo)
            val portName = activePort?.name.stringValue()
            val portDescription = activePort?.description.stringValue()
            val routeHints = listOfNotNull(
                sinkName,
                description,
                portName,
                portDescription,
            )
            return PulseSinkSnapshot(
                sinkName = sinkName,
                sinkIndex = info.index,
                channels = channelCount,
                volume = SystemVolumeSnapshot(
                    currentVolume = volumePercent,
                    maxVolume = LinuxAudioController.VIRTUAL_MAX_VOLUME,
                    isMuted = info.mute != 0,
                ),
                route = AudioRouteSnapshot(
                    id = if (portName == null) sinkName else "$sinkName/$portName",
                    name = portDescription ?: description,
                    isHeadphones = PulseAudioRouteClassifier.isHeadphones(
                        portType = activePort?.type,
                        hints = routeHints,
                    ),
                ),
            )
        }
    }
}

internal object PulseAudioRouteClassifier {
    fun isHeadphones(portType: Int?, hints: List<String>): Boolean {
        if (portType in HEADPHONE_PORT_TYPES) return true
        val normalizedHints = hints.joinToString(" ").lowercase()
        return HEADPHONE_HINTS.any(normalizedHints::contains)
    }

    private val HEADPHONE_PORT_TYPES = setOf(
        3, // PA_DEVICE_PORT_TYPE_HEADPHONES
        6, // PA_DEVICE_PORT_TYPE_HEADSET
        8, // PA_DEVICE_PORT_TYPE_EARPIECE
        15, // PA_DEVICE_PORT_TYPE_BLUETOOTH
        17, // PA_DEVICE_PORT_TYPE_HANDSFREE
    )
    private val HEADPHONE_HINTS = listOf(
        "headphone",
        "headset",
        "bluez_output",
        "a2dp",
        "handsfree",
    )
}

internal interface PulseAudioLibrary : Library {
    fun pa_threaded_mainloop_new(): Pointer?
    fun pa_threaded_mainloop_free(mainloop: Pointer)
    fun pa_threaded_mainloop_start(mainloop: Pointer): Int
    fun pa_threaded_mainloop_stop(mainloop: Pointer)
    fun pa_threaded_mainloop_lock(mainloop: Pointer)
    fun pa_threaded_mainloop_unlock(mainloop: Pointer)
    fun pa_threaded_mainloop_get_api(mainloop: Pointer): Pointer?

    fun pa_context_new(mainloopApi: Pointer, name: String): Pointer?
    fun pa_context_unref(context: Pointer)
    fun pa_context_set_state_callback(
        context: Pointer,
        callback: PulseContextNotifyCallback?,
        userdata: Pointer?,
    )
    fun pa_context_get_state(context: Pointer): Int
    fun pa_context_connect(context: Pointer, server: String?, flags: Int, spawnApi: Pointer?): Int
    fun pa_context_disconnect(context: Pointer)
    fun pa_context_errno(context: Pointer): Int

    fun pa_context_get_server_info(
        context: Pointer,
        callback: PulseServerInfoCallback,
        userdata: Pointer?,
    ): Pointer?
    fun pa_context_get_sink_info_by_name(
        context: Pointer,
        name: String,
        callback: PulseSinkInfoCallback,
        userdata: Pointer?,
    ): Pointer?
    fun pa_context_set_sink_volume_by_name(
        context: Pointer,
        name: String,
        volume: PulseCVolume,
        callback: PulseContextSuccessCallback,
        userdata: Pointer?,
    ): Pointer?
    fun pa_context_subscribe(
        context: Pointer,
        mask: Int,
        callback: PulseContextSuccessCallback?,
        userdata: Pointer?,
    ): Pointer?
    fun pa_context_set_subscribe_callback(
        context: Pointer,
        callback: PulseSubscribeCallback?,
        userdata: Pointer?,
    )

    fun pa_operation_unref(operation: Pointer)
    fun pa_cvolume_set(volume: PulseCVolume, channels: Int, value: Int): Pointer?
    fun pa_strerror(error: Int): String?

    companion object {
        fun load(): PulseAudioLibrary = Native.load("libpulse.so.0", PulseAudioLibrary::class.java)
    }
}

internal fun interface PulseContextNotifyCallback : Callback {
    fun invoke(context: Pointer?, userdata: Pointer?)
}

internal fun interface PulseContextSuccessCallback : Callback {
    fun invoke(context: Pointer?, success: Int, userdata: Pointer?)
}

internal fun interface PulseServerInfoCallback : Callback {
    fun invoke(context: Pointer?, info: Pointer?, userdata: Pointer?)
}

internal fun interface PulseSinkInfoCallback : Callback {
    fun invoke(context: Pointer?, info: Pointer?, endOfList: Int, userdata: Pointer?)
}

internal fun interface PulseSubscribeCallback : Callback {
    fun invoke(context: Pointer?, eventType: Int, index: Int, userdata: Pointer?)
}

@Structure.FieldOrder("format", "rate", "channels")
internal open class PulseSampleSpec : Structure() {
    @JvmField var format: Int = 0
    @JvmField var rate: Int = 0
    @JvmField var channels: Byte = 0
}

@Structure.FieldOrder("channels", "map")
internal open class PulseChannelMap : Structure() {
    @JvmField var channels: Byte = 0
    @JvmField var map: IntArray = IntArray(CHANNELS_MAX)

    private companion object {
        const val CHANNELS_MAX = 32
    }
}

@Structure.FieldOrder("channels", "values")
internal open class PulseCVolume : Structure() {
    @JvmField var channels: Byte = 0
    @JvmField var values: IntArray = IntArray(CHANNELS_MAX)

    companion object {
        const val CHANNELS_MAX = 32
        const val VOLUME_NORM = 0x10000
    }
}

@Structure.FieldOrder(
    "name",
    "description",
    "priority",
    "available",
    "availabilityGroup",
    "type",
)
internal class PulseSinkPortInfo(pointer: Pointer) : Structure(pointer) {
    @JvmField var name: Pointer? = null
    @JvmField var description: Pointer? = null
    @JvmField var priority: Int = 0
    @JvmField var available: Int = 0
    @JvmField var availabilityGroup: Pointer? = null
    @JvmField var type: Int = 0

    init {
        read()
    }
}

@Structure.FieldOrder(
    "userName",
    "hostName",
    "serverVersion",
    "serverName",
    "sampleSpec",
    "defaultSinkName",
    "defaultSourceName",
    "cookie",
    "channelMap",
)
internal class PulseServerInfo(pointer: Pointer) : Structure(pointer) {
    @JvmField var userName: Pointer? = null
    @JvmField var hostName: Pointer? = null
    @JvmField var serverVersion: Pointer? = null
    @JvmField var serverName: Pointer? = null
    @JvmField var sampleSpec: PulseSampleSpec = PulseSampleSpec()
    @JvmField var defaultSinkName: Pointer? = null
    @JvmField var defaultSourceName: Pointer? = null
    @JvmField var cookie: Int = 0
    @JvmField var channelMap: PulseChannelMap = PulseChannelMap()

    init {
        read()
    }
}

@Structure.FieldOrder(
    "name",
    "index",
    "description",
    "sampleSpec",
    "channelMap",
    "ownerModule",
    "volume",
    "mute",
    "monitorSource",
    "monitorSourceName",
    "latency",
    "driver",
    "flags",
    "proplist",
    "configuredLatency",
    "baseVolume",
    "state",
    "volumeSteps",
    "card",
    "portCount",
    "ports",
    "activePort",
    "formatCount",
    "formats",
)
internal class PulseSinkInfo(pointer: Pointer) : Structure(pointer) {
    @JvmField var name: Pointer? = null
    @JvmField var index: Int = 0
    @JvmField var description: Pointer? = null
    @JvmField var sampleSpec: PulseSampleSpec = PulseSampleSpec()
    @JvmField var channelMap: PulseChannelMap = PulseChannelMap()
    @JvmField var ownerModule: Int = 0
    @JvmField var volume: PulseCVolume = PulseCVolume()
    @JvmField var mute: Int = 0
    @JvmField var monitorSource: Int = 0
    @JvmField var monitorSourceName: Pointer? = null
    @JvmField var latency: Long = 0
    @JvmField var driver: Pointer? = null
    @JvmField var flags: Int = 0
    @JvmField var proplist: Pointer? = null
    @JvmField var configuredLatency: Long = 0
    @JvmField var baseVolume: Int = 0
    @JvmField var state: Int = 0
    @JvmField var volumeSteps: Int = 0
    @JvmField var card: Int = 0
    @JvmField var portCount: Int = 0
    @JvmField var ports: Pointer? = null
    @JvmField var activePort: Pointer? = null
    @JvmField var formatCount: Byte = 0
    @JvmField var formats: Pointer? = null

    init {
        read()
    }
}

private fun Pointer?.stringValue(): String? =
    this?.getString(0)?.takeIf(String::isNotBlank)

private fun pulseError(library: PulseAudioLibrary, context: Pointer): String {
    val errorCode = library.pa_context_errno(context)
    return library.pa_strerror(errorCode) ?: "libpulse error $errorCode"
}
