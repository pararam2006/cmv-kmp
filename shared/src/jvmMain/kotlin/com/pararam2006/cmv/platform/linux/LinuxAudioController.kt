package com.pararam2006.cmv.platform.linux

import com.pararam2006.cmv.platform.AudioRouteSnapshot
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class WpctlAudioController(
    private val scope: CoroutineScope,
    private val logger: (String) -> Unit = {},
    private val commandRunner: CommandRunner = ProcessCommandRunner(),
) : LinuxAudioBackend {
    override val backendName: String = "wpctl fallback"

    private val _volume = MutableStateFlow<SystemVolumeSnapshot?>(null)
    override val volume: StateFlow<SystemVolumeSnapshot?> = _volume.asStateFlow()

    private val _route = MutableStateFlow<AudioRouteSnapshot?>(null)
    override val route: StateFlow<AudioRouteSnapshot?> = _route.asStateFlow()

    private var pollingJob: Job? = null

    override suspend fun start() {
        if (pollingJob?.isActive == true) return

        check(isLinux()) { "System audio tracking is only available on Linux" }
        refresh(throwOnFailure = true)
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refresh(throwOnFailure = false)
            }
        }
    }

    override suspend fun stop() {
        pollingJob?.cancelAndJoin()
        pollingJob = null
        _volume.value = null
        _route.value = null
    }

    override suspend fun setVolume(volume: Int) {
        val normalized = volume.coerceIn(0, VIRTUAL_MAX_VOLUME)
        val scalar = String.format(Locale.US, "%.3f", normalized.toDouble() / VIRTUAL_MAX_VOLUME)
        withContext(Dispatchers.IO) {
            commandRunner.run(
                listOf(
                    "wpctl",
                    "set-volume",
                    DEFAULT_AUDIO_SINK,
                    scalar,
                    "--limit",
                    "1.0",
                ),
            ).requireSuccess("Unable to set the default sink volume")
        }
        refresh(throwOnFailure = false)
    }

    private suspend fun refresh(throwOnFailure: Boolean) {
        runCatching {
            coroutineScope {
                val volumeResult = async(Dispatchers.IO) {
                    commandRunner.run(listOf("wpctl", "get-volume", DEFAULT_AUDIO_SINK))
                }
                val routeResult = async(Dispatchers.IO) {
                    commandRunner.run(listOf("wpctl", "inspect", DEFAULT_AUDIO_SINK))
                }

                val parsedVolume = WpctlOutputParser.parseVolume(
                    volumeResult.await().requireSuccess("Unable to read the default sink volume"),
                )
                val parsedRoute = WpctlOutputParser.parseRoute(
                    routeResult.await().requireSuccess("Unable to inspect the default sink"),
                )
                _volume.value = parsedVolume
                _route.value = parsedRoute
            }
        }.onFailure { exception ->
            logger("Linux audio refresh failed: ${exception.message}")
            if (throwOnFailure) throw exception
        }
    }

    private fun isLinux(): Boolean =
        System.getProperty("os.name").contains("linux", ignoreCase = true)

    companion object {
        const val VIRTUAL_MAX_VOLUME = 100
        private const val DEFAULT_AUDIO_SINK = "@DEFAULT_AUDIO_SINK@"
        private const val POLL_INTERVAL_MS = 400L
    }
}

internal object WpctlOutputParser {
    private val volumeRegex = Regex("""Volume:\s*([0-9]+(?:\.[0-9]+)?)""")
    private val propertyRegex = Regex("""(?m)^\s*\*?\s*([a-zA-Z0-9_.-]+)\s*=\s*"([^"]*)"\s*$""")

    fun parseVolume(output: String): SystemVolumeSnapshot {
        val scalar = volumeRegex.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: error("Unexpected wpctl volume output: ${output.trim()}")
        return SystemVolumeSnapshot(
            currentVolume = (scalar * LinuxAudioController.VIRTUAL_MAX_VOLUME)
                .roundToInt()
                .coerceIn(0, LinuxAudioController.VIRTUAL_MAX_VOLUME),
            maxVolume = LinuxAudioController.VIRTUAL_MAX_VOLUME,
            isMuted = output.contains("MUTED", ignoreCase = true),
        )
    }

    fun parseRoute(output: String): AudioRouteSnapshot {
        val properties = propertyRegex.findAll(output).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
        val id = properties["node.name"]
            ?: properties["object.path"]
            ?: error("Default sink has no stable identifier")
        val name = properties["node.description"]
            ?: properties["node.nick"]
            ?: id
        val routeHints = listOfNotNull(
            id,
            name,
            properties["device.form-factor"],
            properties["api.alsa.path"],
            properties["device.profile.name"],
            properties["device.profile.description"],
        ).joinToString(" ").lowercase()
        val isHeadphones = HEADPHONE_HINTS.any(routeHints::contains)
        return AudioRouteSnapshot(id = id, name = name, isHeadphones = isHeadphones)
    }

    private val HEADPHONE_HINTS = listOf(
        "headphone",
        "headset",
        "bluez_output",
        "a2dp",
    )
}

internal fun interface CommandRunner {
    suspend fun run(command: List<String>): CommandResult
}

internal data class CommandResult(
    val exitCode: Int,
    val output: String,
) {
    fun requireSuccess(message: String): String {
        check(exitCode == 0) {
            "$message (exit=$exitCode): ${output.trim()}"
        }
        return output
    }
}

internal class ProcessCommandRunner : CommandRunner {
    override suspend fun run(command: List<String>): CommandResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Command timed out: ${command.firstOrNull()}")
        }
        CommandResult(
            exitCode = process.exitValue(),
            output = process.inputStream.bufferedReader().use { it.readText() },
        )
    }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 3L
    }
}
