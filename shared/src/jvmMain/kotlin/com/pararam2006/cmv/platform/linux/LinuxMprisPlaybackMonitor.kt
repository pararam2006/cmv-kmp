package com.pararam2006.cmv.platform.linux

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.platform.MediaPlaybackMonitor
import com.pararam2006.cmv.platform.MediaPlayerSnapshot
import com.pararam2006.cmv.platform.PlaybackStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

class LinuxMprisPlaybackMonitor(
    private val scope: CoroutineScope,
    private val logger: (String) -> Unit = {},
) : MediaPlaybackMonitor {
    private val lifecycleMutex = Mutex()
    private val snapshots = ConcurrentHashMap<String, MediaPlayerSnapshot>()
    private val owners = ConcurrentHashMap<String, String>()
    private val activitySequence = AtomicLong(0)
    private val signalSubscriptions = mutableListOf<AutoCloseable>()

    private val _players = MutableStateFlow<List<MediaPlayerSnapshot>>(emptyList())
    override val players: StateFlow<List<MediaPlayerSnapshot>> = _players.asStateFlow()

    private val _activePlayer = MutableStateFlow<MediaPlayerSnapshot?>(null)
    override val activePlayer: StateFlow<MediaPlayerSnapshot?> = _activePlayer.asStateFlow()

    @Volatile
    private var connection: DBusConnection? = null

    override suspend fun start() = lifecycleMutex.withLock {
        if (connection != null) return@withLock
        check(isLinux()) { "MPRIS is only available on Linux" }

        withContext(Dispatchers.IO) {
            val newConnection = DBusConnectionBuilder.forSessionBus().build()
            try {
                registerSignals(newConnection)
                connection = newConnection
                listBusNames(newConnection)
                    .filter(::isMprisName)
                    .forEach { busName -> refreshPlayer(newConnection, busName) }
                logger("MPRIS monitor connected; players=${snapshots.size}")
            } catch (exception: Exception) {
                signalSubscriptions.forEach { runCatching { it.close() } }
                signalSubscriptions.clear()
                runCatching { newConnection.close() }
                throw exception
            }
        }
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            signalSubscriptions.forEach { subscription -> runCatching { subscription.close() } }
            signalSubscriptions.clear()
            val oldConnection = connection
            connection = null
            runCatching { oldConnection?.close() }
            snapshots.clear()
            owners.clear()
            publishSnapshots()
        }
    }

    private fun registerSignals(newConnection: DBusConnection) {
        val nameOwnerHandler = DBusSigHandler<DBus.NameOwnerChanged> { signal ->
            if (!isMprisName(signal.name)) return@DBusSigHandler
            scope.launch(Dispatchers.IO) {
                if (signal.newOwner.isBlank()) {
                    removePlayer(signal.name)
                } else {
                    refreshPlayer(newConnection, signal.name)
                }
            }
        }
        signalSubscriptions += newConnection.addSigHandler(
            DBus.NameOwnerChanged::class.java,
            nameOwnerHandler,
        )

        val propertiesHandler = DBusSigHandler<Properties.PropertiesChanged> { signal ->
            if (signal.path != MPRIS_OBJECT_PATH) return@DBusSigHandler
            if (signal.interfaceName != MPRIS_ROOT_INTERFACE &&
                signal.interfaceName != MPRIS_PLAYER_INTERFACE
            ) {
                return@DBusSigHandler
            }

            val source = signal.source ?: return@DBusSigHandler
            val busName = owners.entries.firstOrNull { it.value == source }?.key
                ?: return@DBusSigHandler
            scope.launch(Dispatchers.IO) {
                refreshPlayer(newConnection, busName)
            }
        }
        signalSubscriptions += newConnection.addSigHandler(
            Properties.PropertiesChanged::class.java,
            propertiesHandler,
        )
    }

    private fun refreshPlayer(newConnection: DBusConnection, busName: String) {
        if (connection !== newConnection && connection != null) return
        runCatching {
            val owner = newConnection.getDBusOwnerName(busName)
            val properties = newConnection.getRemoteObject(
                busName,
                MPRIS_OBJECT_PATH,
                Properties::class.java,
            )
            val rootProperties = properties.GetAll(MPRIS_ROOT_INTERFACE)
            val playerProperties = properties.GetAll(MPRIS_PLAYER_INTERFACE)

            val identity = rootProperties.stringValue("Identity")
                ?.takeIf(String::isNotBlank)
                ?: fallbackIdentity(busName)
            val desktopEntry = rootProperties.stringValue("DesktopEntry")
                ?.removeSuffix(".desktop")
                ?.takeIf(String::isNotBlank)
            val resolvedDesktopEntry = desktopEntry
                ?: inferMprisDesktopEntryId(identity, desktopEntryIds)
            val desktopMetadata = resolvedDesktopEntry?.let(::readDesktopEntry)
            val appId = resolvedDesktopEntry ?: fallbackAppId(busName, identity)
            val label = desktopMetadata?.name?.takeIf(String::isNotBlank) ?: identity
            val metadata = playerProperties.mapValue("Metadata")
            val title = metadata?.stringValue("xesam:title")?.takeIf(String::isNotBlank)
            val artist = metadata?.stringListValue("xesam:artist")
                ?.filter(String::isNotBlank)
                ?.joinToString(", ")
                ?.takeIf(String::isNotBlank)
            val status = when (playerProperties.stringValue("PlaybackStatus")) {
                "Playing" -> PlaybackStatus.PLAYING
                "Paused" -> PlaybackStatus.PAUSED
                else -> PlaybackStatus.STOPPED
            }

            owners[busName] = owner
            val snapshot = MediaPlayerSnapshot(
                app = AppInfo(
                    label = label,
                    iconUri = desktopMetadata?.iconUri.orEmpty(),
                    packageName = appId,
                    name = appId,
                ),
                instanceId = busName,
                playbackStatus = status,
                trackTitle = title,
                trackArtist = artist,
                lastActivitySequence = activitySequence.incrementAndGet(),
            )
            val previous = snapshots.put(busName, snapshot)
            if (previous == null || previous.app.packageName != appId) {
                logger("MPRIS player discovered: $label ($appId), bus=$busName")
            }
            publishSnapshots()
        }.onFailure { exception ->
            logger("Unable to refresh MPRIS player $busName: ${exception.message}")
            if (runCatching { busName !in listBusNames(newConnection) }.getOrDefault(true)) {
                removePlayer(busName)
            }
        }
    }

    private fun removePlayer(busName: String) {
        owners.remove(busName)
        if (snapshots.remove(busName) != null) {
            logger("MPRIS player disappeared: $busName")
            publishSnapshots()
        }
    }

    private fun publishSnapshots() {
        val allPlayers = snapshots.values.sortedWith(
            compareBy<MediaPlayerSnapshot> { it.app.label.lowercase() }
                .thenBy { it.instanceId },
        )
        _players.value = allPlayers

        val current = _activePlayer.value
        _activePlayer.value = allPlayers.firstOrNull {
            it.instanceId == current?.instanceId && it.playbackStatus == PlaybackStatus.PLAYING
        } ?: allPlayers
            .asSequence()
            .filter { it.playbackStatus == PlaybackStatus.PLAYING }
            .maxByOrNull { it.lastActivitySequence }
    }

    private fun listBusNames(newConnection: DBusConnection): Sequence<String> {
        val dbus = newConnection.getRemoteObject(
            DBUS_SERVICE,
            DBUS_OBJECT_PATH,
            DBus::class.java,
        )
        return dbus.ListNames().asSequence()
    }

    private fun isLinux(): Boolean =
        System.getProperty("os.name").contains("linux", ignoreCase = true)

    private fun isMprisName(name: String): Boolean = name.startsWith(MPRIS_BUS_PREFIX)

    private fun fallbackIdentity(busName: String): String =
        busAppId(busName).replace('-', ' ').replaceFirstChar { it.uppercase() }

    private fun fallbackAppId(busName: String, identity: String): String {
        val busAppId = busAppId(busName)
        if (busAppId != GENERIC_CHROMIUM_APP_ID) return busAppId

        val identityAppId = identity.toStableAppId()
        return identityAppId.takeUnless {
            it.isBlank() || it in GENERIC_CHROMIUM_IDENTITIES
        } ?: busAppId
    }

    private fun busAppId(busName: String): String =
        busName.removePrefix(MPRIS_BUS_PREFIX).substringBefore(".instance")

    private val desktopEntryIds: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        desktopApplicationDirectories()
            .asSequence()
            .flatMap { directory ->
                directory.listFiles()
                    .orEmpty()
                    .asSequence()
            }
            .filter(File::isFile)
            .map(File::getName)
            .filter { it.endsWith(".desktop", ignoreCase = true) }
            .map { it.removeSuffix(".desktop") }
            .toSet()
    }

    private fun readDesktopEntry(id: String): DesktopEntryMetadata? {
        val fileName = "$id.desktop"
        val desktopFile = desktopApplicationDirectories()
            .asSequence()
            .map { directory -> File(directory, fileName) }
            .firstOrNull(File::isFile)
            ?: return null

        var name: String? = null
        var icon: String? = null
        runCatching {
            desktopFile.useLines { lines ->
                var inDesktopEntry = false
                lines.forEach { line ->
                    when {
                        line == "[Desktop Entry]" -> inDesktopEntry = true
                        line.startsWith('[') && line != "[Desktop Entry]" -> inDesktopEntry = false
                        inDesktopEntry && line.startsWith("Name=") && name == null ->
                            name = line.substringAfter('=')
                        inDesktopEntry && line.startsWith("Icon=") && icon == null ->
                            icon = line.substringAfter('=')
                    }
                }
            }
        }
        return DesktopEntryMetadata(
            name = name,
            iconUri = icon?.let(::resolveIcon).orEmpty(),
        )
    }

    private fun desktopApplicationDirectories(): List<File> {
        val dataHome = System.getenv("XDG_DATA_HOME")
            ?.takeIf(String::isNotBlank)
            ?: File(System.getProperty("user.home"), ".local/share").path
        val dataDirs = System.getenv("XDG_DATA_DIRS")
            ?.takeIf(String::isNotBlank)
            ?.split(':')
            ?: listOf("/usr/local/share", "/usr/share")
        return (listOf(dataHome) + dataDirs).map { File(it, "applications") }
    }

    private fun resolveIcon(icon: String): String {
        val directFile = File(icon)
        if (directFile.isFile) return directFile.toURI().toString()

        val iconNames = if (icon.substringAfterLast('.', "") in ICON_EXTENSIONS) {
            listOf(icon)
        } else {
            ICON_EXTENSIONS.map { extension -> "$icon.$extension" }
        }
        val candidates = buildList {
            iconNames.forEach { iconName ->
                add(File("/usr/share/pixmaps", iconName))
                ICON_SIZES.forEach { size ->
                    add(File("/usr/share/icons/hicolor/$size/apps", iconName))
                }
            }
        }
        return candidates.firstOrNull(File::isFile)?.toURI()?.toString().orEmpty()
    }

    private data class DesktopEntryMetadata(
        val name: String?,
        val iconUri: String,
    )

    private companion object {
        const val DBUS_SERVICE = "org.freedesktop.DBus"
        const val DBUS_OBJECT_PATH = "/org/freedesktop/DBus"
        const val MPRIS_BUS_PREFIX = "org.mpris.MediaPlayer2."
        const val MPRIS_OBJECT_PATH = "/org/mpris/MediaPlayer2"
        const val MPRIS_ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        const val MPRIS_PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        const val GENERIC_CHROMIUM_APP_ID = "chromium"
        val GENERIC_CHROMIUM_IDENTITIES = setOf("chrome", "chromium")
        val ICON_EXTENSIONS = listOf("png", "svg", "xpm")
        val ICON_SIZES = listOf("scalable", "512x512", "256x256", "128x128", "64x64", "48x48")
    }
}

internal fun inferMprisDesktopEntryId(
    identity: String,
    desktopEntryIds: Set<String>,
): String? {
    val normalizedIdentity = identity.normalizedMprisIdentity()
    if (normalizedIdentity.isBlank()) return null
    return desktopEntryIds.firstOrNull {
        it.normalizedMprisIdentity() == normalizedIdentity
    }
}

private fun String.normalizedMprisIdentity(): String =
    lowercase().filter(Char::isLetterOrDigit)

private fun String.toStableAppId(): String =
    lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')

private fun Map<String, Variant<*>>.stringValue(key: String): String? =
    this[key]?.value as? String

private fun Map<String, Variant<*>>.mapValue(key: String): Map<String, Variant<*>>? {
    val value = this[key]?.value as? Map<*, *> ?: return null
    return value.entries.mapNotNull { (entryKey, entryValue) ->
        val stringKey = entryKey as? String ?: return@mapNotNull null
        val variant = entryValue as? Variant<*> ?: return@mapNotNull null
        stringKey to variant
    }.toMap()
}

private fun Map<String, Variant<*>>.stringListValue(key: String): List<String>? {
    val value = this[key]?.value
    return when (value) {
        is Array<*> -> value.filterIsInstance<String>()
        is Iterable<*> -> value.filterIsInstance<String>()
        is String -> listOf(value)
        else -> null
    }
}
