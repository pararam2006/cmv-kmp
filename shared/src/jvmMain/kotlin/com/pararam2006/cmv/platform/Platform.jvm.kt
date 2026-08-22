package com.pararam2006.cmv.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.pararam2006.cmv.data.manager.VolumeLearningManagerImpl
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.domain.service.PlaybackTrackingCoordinator
import com.pararam2006.cmv.platform.linux.LinuxAudioController
import com.pararam2006.cmv.platform.linux.LinuxMprisPlaybackMonitor
import com.pararam2006.cmv.platform.linux.LinuxPlaybackTrackingRuntime
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

private object DesktopPreferenceKeys {
    const val SHOW_SYSTEM_VOLUME_UI = "show_system_volume_ui"
    const val LEARNING_TIME_SECONDS = "learning_time_seconds"
    const val APP_MODE = "app_mode"
    const val USER_STOPPED = "user_stopped"
    const val TRACKS = "tracks"
    const val APPS = "apps"
}

private val desktopPreferences: Preferences by lazy {
    Preferences.userRoot().node("com/pararam2006/cmv")
}
private val desktopJson = Json { ignoreUnknownKeys = true }

actual class SettingsPreferences {
    private val prefs = desktopPreferences
    private val _appModeFlow = MutableStateFlow(readAppMode())
    actual val appModeFlow: StateFlow<AppMode> = _appModeFlow
    private val _userStoppedFlow = MutableStateFlow(getUserStopped())
    actual val userStoppedFlow: StateFlow<Boolean> = _userStoppedFlow

    actual var showSystemVolumeUi: Boolean
        get() = prefs.getBoolean(DesktopPreferenceKeys.SHOW_SYSTEM_VOLUME_UI, true)
        set(value) = prefs.putBoolean(DesktopPreferenceKeys.SHOW_SYSTEM_VOLUME_UI, value)

    actual var learningTimeSeconds: Int
        get() = prefs.getInt(DesktopPreferenceKeys.LEARNING_TIME_SECONDS, 15)
        set(value) = prefs.putInt(DesktopPreferenceKeys.LEARNING_TIME_SECONDS, value)

    actual var appMode: AppMode
        get() = _appModeFlow.value
        set(value) {
            prefs.put(DesktopPreferenceKeys.APP_MODE, value.name)
            _appModeFlow.value = value
        }

    actual fun getUserStopped(): Boolean =
        prefs.getBoolean(DesktopPreferenceKeys.USER_STOPPED, false)

    actual fun setUserStopped(new: Boolean) {
        prefs.putBoolean(DesktopPreferenceKeys.USER_STOPPED, new)
        _userStoppedFlow.value = new
    }

    actual fun isSystemVolumeUiEnabled(): Boolean = showSystemVolumeUi

    private fun readAppMode(): AppMode = runCatching {
        AppMode.valueOf(
            prefs.get(DesktopPreferenceKeys.APP_MODE, AppMode.LEARNING.name),
        )
    }.getOrDefault(AppMode.LEARNING)
}

actual class SystemService internal constructor(
    private val playbackRuntime: PlaybackTrackingRuntime = UnsupportedPlaybackTrackingRuntime,
) {
    actual fun isNotificationServiceSupported(): Boolean = playbackRuntime.isSupported
    actual fun isNotificationServiceEnabled(): Boolean = playbackRuntime.isSupported
    actual fun toggleService(isOn: Boolean): Boolean =
        if (isOn) playbackRuntime.stop() else playbackRuntime.start()

    actual fun openNotificationSettings() = Unit

    actual fun searchWeb(query: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
                Desktop.getDesktop().browse(URI("https://www.google.com/search?q=$encoded"))
            }
        }
    }
}

private object UnsupportedPlaybackTrackingRuntime : PlaybackTrackingRuntime {
    override val state: StateFlow<PlaybackRuntimeState> = MutableStateFlow(
        PlaybackRuntimeState(
            status = PlaybackRuntimeStatus.UNAVAILABLE,
            message = "Playback tracking runtime was not configured",
        ),
    )
    override val isSupported: Boolean = false
    override fun start(): Boolean = false
    override fun stop(): Boolean = true
}

actual fun isDynamicColorAvailable(): Boolean = false

@Composable
actual fun dynamicLightColorScheme(): ColorScheme = lightColorScheme()

@Composable
actual fun dynamicDarkColorScheme(): ColorScheme = darkColorScheme()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

val jvmPlatformModule = module {
    single(named("AppScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { SettingsPreferences() }
    single<TrackVolumeRepository> { PersistentTrackVolumeRepository() }
    single<AppsInfoRepository> { PersistentAppsInfoRepository() }
    single {
        LinuxMprisPlaybackMonitor(
            scope = get(named("AppScope")),
            logger = ::desktopLog,
        )
    }
    single<MediaPlaybackMonitor> { get<LinuxMprisPlaybackMonitor>() }
    single {
        LinuxAudioController(
            scope = get(named("AppScope")),
            logger = ::desktopLog,
        )
    }
    single<SystemVolumeController> { get<LinuxAudioController>() }
    single<AudioRouteMonitor> { get<LinuxAudioController>() }
    single<HeadphonesRepository> {
        DesktopHeadphonesRepository(get(), get(named("AppScope")))
    }
    single<AppDiscoveryService> { DesktopAppDiscoveryService(get(), get()) }
    single<VolumeLearningManager> {
        val settings = get<SettingsPreferences>()
        VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = get(),
            appModeFlow = settings.appModeFlow,
            learningTimeSeconds = { settings.learningTimeSeconds },
            scope = get(named("AppScope")),
            nowMillis = System::currentTimeMillis,
            logger = ::desktopLog,
        )
    }
    single {
        PlaybackTrackingCoordinator(
            appsInfoRepository = get(),
            trackVolumeRepository = get(),
            volumeLearningManager = get(),
            scope = get(named("AppScope")),
            logger = ::desktopLog,
        )
    }
    single<PlaybackTrackingRuntime> {
        LinuxPlaybackTrackingRuntime(
            scope = get(named("AppScope")),
            playbackMonitor = get(),
            volumeController = get(),
            routeMonitor = get(),
            appsInfoRepository = get(),
            coordinator = get(),
            serviceStateHolder = get(),
            logger = ::desktopLog,
        )
    }
    single { SystemService(get()) }
}

private fun desktopLog(message: String) = println("[CMV] $message")

private class PersistentTrackVolumeRepository : TrackVolumeRepository {
    private val mutex = Mutex()
    private val tracks = MutableStateFlow(loadTracks())

    override fun getAllTrackVolumes(): Flow<List<TrackVolume>> = tracks

    override suspend fun getTrackVolume(title: String, artist: String?): TrackVolume? =
        tracks.value.firstOrNull { track ->
            track.trackTitle.equals(title, ignoreCase = true) &&
                track.artistName.orEmpty().equals(artist.orEmpty(), ignoreCase = true)
        }

    override suspend fun getTrackVolumeById(id: Int): TrackVolume? =
        tracks.value.firstOrNull { it.id == id }

    override suspend fun saveTrackVolume(trackVolume: TrackVolume) = mutate { current ->
        val savedTrack = if (trackVolume.id == 0) {
            trackVolume.copy(id = (current.maxOfOrNull { it.id } ?: 0) + 1)
        } else {
            trackVolume
        }
        current.filterNot { it.id == savedTrack.id } + savedTrack
    }

    override suspend fun deleteTrackVolume(trackVolume: TrackVolume) {
        deleteTrackVolumeById(trackVolume.id)
    }

    override suspend fun deleteTrackVolumeById(id: Int) = mutate { current ->
        current.filterNot { it.id == id }
    }

    private suspend fun mutate(transform: (List<TrackVolume>) -> List<TrackVolume>) {
        mutex.withLock {
            val updated = transform(tracks.value)
            desktopPreferences.put(
                DesktopPreferenceKeys.TRACKS,
                desktopJson.encodeToString(updated),
            )
            tracks.value = updated
        }
    }

    private fun loadTracks(): List<TrackVolume> = runCatching {
        desktopJson.decodeFromString<List<TrackVolume>>(
            desktopPreferences.get(DesktopPreferenceKeys.TRACKS, "[]"),
        )
    }.getOrDefault(emptyList())
}

private class PersistentAppsInfoRepository : AppsInfoRepository {
    private val mutex = Mutex()
    private val apps = MutableStateFlow(loadApps())

    override fun getAllAppsInfo(): Flow<List<AppInfo>> = apps
    override fun getAllSelectedAppsInfo(): Flow<List<AppInfo>> =
        apps.map { values -> values.filter { it.selected } }

    override suspend fun getAppInfo(packageName: String): AppInfo? =
        apps.value.firstOrNull { it.packageName == packageName }

    override suspend fun selectApp(id: Int) = updateByIndex(id, true)
    override suspend fun selectApp(packageName: String) = updateByPackage(packageName, true)
    override suspend fun unselectApp(id: Int) = updateByIndex(id, false)
    override suspend fun unselectApp(packageName: String) = updateByPackage(packageName, false)
    override suspend fun getAppInfo(id: Int): AppInfo? = apps.value.getOrNull(id)

    override suspend fun addAppInfo(appInfo: AppInfo) = mutate { current ->
        current.filterNot { it.packageName == appInfo.packageName } + appInfo
    }

    override suspend fun deleteAppInfo(appInfo: AppInfo) = mutate { current ->
        current.filterNot { it.packageName == appInfo.packageName }
    }

    override suspend fun deleteAppInfo(id: Int) = mutate { current ->
        current.mapIndexedNotNull { index, app -> app.takeUnless { index == id } }
    }

    private suspend fun updateByPackage(packageName: String, selected: Boolean) = mutate { current ->
        current.map { app ->
            if (app.packageName == packageName) app.copy(selected = selected) else app
        }
    }

    private suspend fun updateByIndex(id: Int, selected: Boolean) = mutate { current ->
        current.mapIndexed { index, app ->
            if (index == id) app.copy(selected = selected) else app
        }
    }

    private suspend fun mutate(transform: (List<AppInfo>) -> List<AppInfo>) {
        mutex.withLock {
            val updated = transform(apps.value)
            desktopPreferences.put(
                DesktopPreferenceKeys.APPS,
                desktopJson.encodeToString(updated),
            )
            apps.value = updated
        }
    }

    private fun loadApps(): List<AppInfo> = runCatching {
        desktopJson.decodeFromString<List<AppInfo>>(
            desktopPreferences.get(DesktopPreferenceKeys.APPS, "[]"),
        )
    }.getOrDefault(emptyList())
}

private class DesktopHeadphonesRepository(
    private val routeMonitor: AudioRouteMonitor,
    scope: CoroutineScope,
) : HeadphonesRepository {
    private val headsetState = MutableSharedFlow<Boolean>(replay = 1).apply { tryEmit(false) }
    override val isHeadsetFlow: SharedFlow<Boolean> = headsetState

    init {
        scope.launch {
            routeMonitor.route.collect { route ->
                headsetState.emit(route?.isHeadphones == true)
            }
        }
    }

    override fun computeIsHeadsetConnected(): Boolean =
        routeMonitor.route.value?.isHeadphones == true
}

private class DesktopAppDiscoveryService(
    private val playbackMonitor: MediaPlaybackMonitor,
    private val appsInfoRepository: AppsInfoRepository,
) : AppDiscoveryService {
    override suspend fun discoverApps(): List<AppInfo> {
        runCatching { playbackMonitor.start() }
            .onFailure { desktopLog("MPRIS discovery unavailable: ${it.message}") }
        val observedApps = playbackMonitor.players.value.map { it.app }
        val savedApps = appsInfoRepository.getAllAppsInfo().first()
        return (observedApps + savedApps)
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
