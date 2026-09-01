package com.pararam2006.cmv

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pararam2006.cmv.core.service.ListenerServiceStateHolder
import com.pararam2006.cmv.core.di.sharedModule
import com.pararam2006.cmv.platform.MediaPlaybackMonitor
import com.pararam2006.cmv.platform.PlaybackRuntimeStatus
import com.pararam2006.cmv.platform.PlaybackTrackingRuntime
import com.pararam2006.cmv.platform.SettingsPreferences
import com.pararam2006.cmv.platform.jvmPlatformModule
import com.pararam2006.cmv.platform.linux.LinuxAutostartManager
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.compose_multiplatform
import java.awt.SystemTray
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.qualifier.named
import org.koin.core.context.startKoin
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

fun main(args: Array<String>) {
    DesktopFileLogger.install()
    val instanceLock = acquireInstanceLock() ?: return
    installAutostartEntry()
    val koin = startKoin {
        modules(sharedModule, jvmPlatformModule)
    }.koin

    val runtime = koin.get<PlaybackTrackingRuntime>()
    val playbackMonitor = koin.get<MediaPlaybackMonitor>()
    val settings = koin.get<SettingsPreferences>()
    val stateHolder = koin.get<ListenerServiceStateHolder>()
    val appScope = koin.get<CoroutineScope>(named("AppScope"))
    stateHolder.setUserStopped(settings.getUserStopped())
    if (!settings.getUserStopped()) runtime.start()

    try {
        application {
            val traySupported = remember { SystemTray.isSupported() }
            val backgroundLaunch = remember { "--background" in args }
            var isWindowVisible by remember {
                mutableStateOf(!backgroundLaunch || !traySupported)
            }
            val runtimeState by runtime.state.collectAsState()
            val isRunning = runtimeState.status == PlaybackRuntimeStatus.RUNNING ||
                runtimeState.status == PlaybackRuntimeStatus.STARTING

            LaunchedEffect(instanceLock) {
                while (true) {
                    delay(INSTANCE_SIGNAL_POLL_MS.milliseconds)
                    val showRequested = withContext(Dispatchers.IO) {
                        instanceLock.consumeShowRequest()
                    }
                    if (showRequested) isWindowVisible = true
                }
            }

            fun toggleTracking() {
                settings.setUserStopped(isRunning)
                stateHolder.setUserStopped(isRunning)
                if (isRunning) runtime.stop() else runtime.start()
            }

            fun shutdown() {
                runtime.stop()
                runBlocking { runCatching { playbackMonitor.stop() } }
                appScope.cancel()
                exitApplication()
            }

            if (traySupported) {
                Tray(
                    icon = painterResource(Res.drawable.compose_multiplatform),
                    tooltip = "Custom Music Volume",
                    onAction = { isWindowVisible = true },
                    menu = {
                        Item(
                            text = "Open",
                            onClick = { isWindowVisible = true },
                        )
                        Item(
                            text = if (isRunning) "Stop tracking" else "Start tracking",
                            onClick = ::toggleTracking,
                        )
                        Separator()
                        Item(
                            text = "Exit",
                            onClick = ::shutdown,
                        )
                    },
                )
            }

            Window(
                visible = isWindowVisible,
                onCloseRequest = {
                    if (traySupported) isWindowVisible = false else shutdown()
                },
                title = "Custom Music Volume",
            ) {
                App(appVersion = "1.2")
            }
        }
    } finally {
        instanceLock.close()
    }
}

private fun installAutostartEntry() {
    val launcherPath = LinuxAutostartManager.packagedLauncherPath() ?: return
    runCatching {
        LinuxAutostartManager().ensureInstalled(launcherPath)
    }.onFailure { exception ->
        println("[CMV] Unable to configure Linux autostart: ${exception.message}")
    }
}

private fun acquireInstanceLock(): InstanceLock? {
    val runtimeDirectory = System.getenv("XDG_RUNTIME_DIR")
        ?.takeIf(String::isNotBlank)
        ?: System.getProperty("java.io.tmpdir")
    val lockPath = Path.of(runtimeDirectory, "custom-music-volume.lock")
    val signalPath = Path.of(runtimeDirectory, "custom-music-volume.show")
    return runCatching {
        val channel = FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        val lock = channel.tryLock()
        if (lock == null) {
            channel.close()
            signalExistingInstance(signalPath)
            null
        } else {
            InstanceLock(
                channel = channel,
                lock = lock,
                signalPath = signalPath,
                lastSignal = readSignal(signalPath),
            )
        }
    }.getOrNull()
}

private fun signalExistingInstance(signalPath: Path) {
    runCatching {
        Files.writeString(
            signalPath,
            System.nanoTime().toString(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }
}

private fun readSignal(signalPath: Path): String? =
    runCatching { Files.readString(signalPath) }.getOrNull()

private class InstanceLock(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val signalPath: Path,
    private var lastSignal: String?,
) : AutoCloseable {
    fun consumeShowRequest(): Boolean {
        val currentSignal = readSignal(signalPath)
        if (currentSignal == null || currentSignal == lastSignal) return false
        lastSignal = currentSignal
        return true
    }

    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }
}

private const val INSTANCE_SIGNAL_POLL_MS = 300L
