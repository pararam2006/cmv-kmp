package com.pararam2006.cmv.core.service

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import com.pararam2006.cmv.R
import com.pararam2006.cmv.core.Constants.SMALL_DELAY
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.ui.changeMode.AppMode
import com.pararam2006.cmv.utils.SettingsPreferences
import com.pararam2006.cmv.utils.logDebug
import com.pararam2006.cmv.utils.logLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import timber.log.Timber
import kotlin.math.roundToInt

private sealed interface NotificationListenerServiceEvent {
    data class VolumeChanged(
        val newVolume: Int,
        val isHeadset: Boolean,
        val hasFocus: Boolean,
    ) : NotificationListenerServiceEvent
}

class MyNotificationListenerService : NotificationListenerService(), KoinComponent {
    private val context: Application by inject()
    private val appsInfoRepository: AppsInfoRepository by inject()
    private val serviceStateHolder: MyNotificationListenerServiceStateHolder by inject()
    private val trackVolumeRepository: TrackVolumeRepository by inject()
    private val manager: VolumeLearningManager by inject()
    private val settingsPreferences: SettingsPreferences by inject()
    private val headphonesDetector: HeadphonesRepository by inject()
    private val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selectedAppsInfoFlow = appsInfoRepository.getAllSelectedAppsInfo()
    private val appModeFlow = settingsPreferences.appModeFlow
    private var appMode: AppMode = settingsPreferences.getAppMode()
    private val events = Channel<NotificationListenerServiceEvent>(Channel.UNLIMITED)
    private val isHeadsetFlow = headphonesDetector.isHeadsetFlow
    private var lastTrackInfo: Pair<String, String?>? = null
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    companion object {
        private const val TAG = "CMV.Service"

        const val ACTION_STOP: String = "com.pararam2006.cmv.ACTION_STOP_LISTENER"
        const val ACTION_STATE_CHANGED: String = "com.pararam2006.cmv.ACTION_LISTENER_STATE_CHANGED"
        const val EXTRA_CONNECTED: String = "extra_connected"
        const val EXTRA_USER_STOPPED: String = "extra_user_stopped"
    }

    private val instanceId = hashCode()

    private fun handleVolumeChanged(
        newVolume: Int,
        isHeadset: Boolean,
        hasFocus: Boolean,
    ) {
        manager.onHeadsetStateChanged(isHeadset)
        manager.onAudioFocusChanged(hasFocus)
        manager.onVolumeChanged(newVolume)
    }

    private suspend fun handleEvent(event: NotificationListenerServiceEvent) {
        when (event) {
            is NotificationListenerServiceEvent.VolumeChanged -> runIfAppInList {
                handleVolumeChanged(
                    newVolume = event.newVolume,
                    isHeadset = event.isHeadset,
                    hasFocus = event.hasFocus,
                )
            }
        }
    }

    init {
        logLifecycle("init: instanceId=$instanceId, serviceScope started")

        serviceScope.launch {
            logLifecycle("selectedAppsInfoFlow started")
            selectedAppsInfoFlow.collect { newList ->
                logDebug("selectedApps count=${newList.size}")
                serviceStateHolder.setSelectedApps(newList)
            }
        }

        serviceScope.launch {
            logLifecycle("userStoppedFlow started")
            settingsPreferences.userStoppedFlow.collect { new ->
                logDebug("userStopped value=$new")
                serviceStateHolder.setUserStopped(new)
            }
        }

        serviceScope.launch {
            logLifecycle("appModeFlow started")
            appModeFlow.collect { newMode ->
                if (newMode != appMode) {
                    val previous = appMode
                    appMode = newMode
                    logDebug("appMode changed $previous -> $newMode")
                }
            }
        }

        serviceScope.launch {
            logLifecycle("events channel started")
            for (event in events) {
                handleEvent(event)
            }
        }

        serviceScope.launch {
            logLifecycle("isHeadsetFlow started")
            isHeadsetFlow.collect { isHeadset ->
                logDebug("isHeadset=$isHeadset")
                manager.onHeadsetStateChanged(isHeadset)
                updateForegroundNotification(isHeadset)
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val hasFocus = audioManager.isMusicActive

                serviceScope.launch {
                    val isHeadset = isHeadsetFlow.first()
                    events.trySend(
                        NotificationListenerServiceEvent.VolumeChanged(
                            newVolume = newVolume,
                            isHeadset = isHeadset,
                            hasFocus = hasFocus,
                        )
                    )
                    serviceStateHolder.setCurrentTrackTitle(lastTrackInfo?.first)
                    serviceStateHolder.setCurrentTrackArtist(lastTrackInfo?.second)
                    logDebug("VOLUME_CHANGED_ACTION: newVolume=$newVolume/$maxVolume, isHeadset=$isHeadset, hasFocus=$hasFocus, currentTrack=${lastTrackInfo?.first}/${lastTrackInfo?.second}")
                }
            }
        }
    }

    // Callback for metadata changes on the active media controller
    private val metadataCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            logDebug("MediaController.onMetadataChanged()")
            metadata?.let { handleMetadata(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            logDebug("MediaController.onPlaybackStateChanged(), isPlaying=$isPlaying")
            manager.onPlaybackStateChanged(isPlaying)
        }
    }

    // Listener for active session changes (e.g. user switches from Spotify to YandexMusic)
    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            logDebug("Active sessions changed, count=${controllers?.size ?: 0}")
            controllers?.forEach { c ->
                logDebug("  Session: ${c.packageName}, tag=${c.sessionToken}")
            }

            // Unregister from the old controller
            activeController?.unregisterCallback(metadataCallback)
            activeController = null

            // Register on the first (most recent) active controller
            val controller = controllers?.firstOrNull()
            if (controller != null) {
                logDebug("Attaching to controller: ${controller.packageName}")
                activeController = controller
                controller.registerCallback(metadataCallback)

                // Also process current metadata immediately
                val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                manager.onPlaybackStateChanged(isPlaying)
                controller.metadata?.let { handleMetadata(it) }
            } else {
                logDebug("No active media sessions")
            }
        }

    private fun updateForegroundNotification(isHeadset: Boolean) {
        val stopIntent = Intent(context, MyNotificationListenerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (isHeadset) "Умный режим включён" else "Спит (наушники отключены)"

        val notification =
            NotificationCompat.Builder(context, "CHANNEL_ID")
//                .setContentTitle("CMV")
                .setContentText(text)
                .setSmallIcon(R.drawable.outline_edit_audio_24)
                .addAction(R.drawable.outline_mode_off_on_24, "Выключить", stopPendingIntent)
                .setOngoing(true)
                .build()

        // If we're already running as foreground, updating via startForeground() is the most reliable.
        // Fallback to notify() in case the system rejects it for any reason.
        try {
            startForeground(1, notification)
        } catch (e: Exception) {
            Timber.w(e, "startForeground() failed during update, falling back to notify()")
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.notify(1, notification)
        }
    }

    private fun broadcastState() {
        val serviceState =
            serviceStateHolder.state.value
        val isConnected = serviceState.isConnected
        val userStopped = serviceState.userStopped

        try {
            val intent = Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_CONNECTED, isConnected)
                .putExtra(EXTRA_USER_STOPPED, userStopped)
            sendBroadcast(intent)
            logLifecycle("broadcastState: connected=$isConnected, userStopped=$userStopped")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: broadcastState() failed")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        logLifecycle("instanceId=$instanceId, intent=$intent")
        return try {
            super.onBind(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: onBind FAILED")
            null
        }
    }

    override fun onCreate() {
        logLifecycle("START instanceId=$instanceId")
        try {
            super.onCreate()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: super.onCreate() FAILED")
        }

        // Restore user intent (stopped via UI) across app/service restarts.
        try {
            val userStopped = settingsPreferences.getUserStopped()
            serviceStateHolder.setUserStopped(userStopped)
            logDebug("restored userStopped=$userStopped")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: failed to restore userStopped from prefs")
        }
        broadcastState()

        try {
            createNotificationChannel()

            val stopIntent = Intent(this, MyNotificationListenerService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isHeadset = headphonesDetector.computeIsHeadsetConnected()
            val text = if (isHeadset) "Приложение активно" else "Спит (наушники отключены)"

            val notification =
                NotificationCompat.Builder(this, "CHANNEL_ID").setContentTitle("CMV Service")
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .addAction(R.drawable.ic_launcher_foreground, "Выключить", stopPendingIntent)
                    .setOngoing(true).build()

            startForeground(1, notification)
            logDebug("foreground started, isHeadset=$isHeadset")

            // Register Volume Receiver
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(volumeReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(volumeReceiver, filter)
            }
            logLifecycle("volumeReceiver registered")

            // Listen for Volume Commands from Manager
            serviceScope.launch {
                logLifecycle("volumeCommands started")
                manager.volumeCommands.collect { expectedVolume ->
                    // Small delay to let the system (especially on Samsung with Absolute Volume) 
                    // process the track change and audio focus transition first.
                    delay(SMALL_DELAY)

                    val beforeVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val shouldShowSystemUi = settingsPreferences.isSystemVolumeUiEnabled()
                    val volumeFlags = if (shouldShowSystemUi) AudioManager.FLAG_SHOW_UI else 0
                    logDebug("Manager requested volume change: before=$beforeVolume, target=$expectedVolume, showSystemUi=$shouldShowSystemUi")
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC, expectedVolume, volumeFlags
                    )
                    val afterVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    logDebug("Volume applied: after=$afterVolume, target=$expectedVolume")
                }
            }

            serviceScope.launch {
                logLifecycle("manager.debugState started")
                manager.debugState.collect { st ->
                    Timber.tag(TAG).v(
                        "managerState: track=${st.currentTrackTitle}/${st.currentTrackArtist}, " +
                                "base=${st.baseVolume}, offset=${st.currentLearnedOffset}, " +
                                "expectedVol=${st.expectedProgrammaticVolume}, headset=${st.isHeadsetConnected}, " +
                                "focus=${st.hasAudioFocus}, playing=${st.isPlaying}, playingMs=${st.accumulatedPlayingTimeMs}"
                    )
                }
            }
            logDebug("COMPLETE instanceId=$instanceId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: onCreate FAILED during foreground setup")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logDebug("action=${intent?.action}, flags=$flags, startId=$startId")
        if (intent?.action == ACTION_STOP) {
            logLifecycle("ACTION_STOP — unbind and remove foreground")
            try {
                settingsPreferences.setUserStopped(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist userStopped=true")
            }
            broadcastState()

            try {
                val componentName = ComponentName(this, MyNotificationListenerService::class.java)
                // Best-effort: ask the OS to disconnect the listener without revoking permission.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    requestUnbind(componentName)
                }
                logLifecycle("requestUnbind() called")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "lifecycle: requestUnbind() failed")
            }

            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                logLifecycle("stopForeground(STOP_FOREGROUND_REMOVE)")
            } catch (_: Exception) {
                // ignore
            }
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.cancel(1)
            } catch (_: Exception) {
                // ignore
            }
            logLifecycle("stopSelf()")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceStateHolder.setConnected(true)
        logLifecycle("registering MediaSessionManager")
        broadcastState()

        try {
            sessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, MyNotificationListenerService::class.java)

            sessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener, componentName
            )
            logDebug("MediaSessionManager listener registered")

            // Process already active sessions
            val activeSessions = sessionManager?.getActiveSessions(componentName)
            logDebug("Currently active sessions: ${activeSessions?.size ?: 0}")
            activeSessions?.forEach { c ->
                manager.onActiveSessionPackageNameChanged(c.packageName)
                logDebug("Active session: ${c.packageName}")
            }

            val controller = activeSessions?.firstOrNull()
            if (controller != null) {
                logDebug("Attaching to current controller: ${controller.packageName}")
                activeController = controller
                controller.registerCallback(metadataCallback)

                val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                serviceScope.launch {
                    manager.onPlaybackStateChanged(isPlaying)
                }

                lastTrackInfo = null
                logDebug("onListenerConnected: lastTrackInfo reset to force volume re-apply")

                controller.metadata?.let { handleMetadata(it) }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: onListenerConnected FAILED")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        serviceStateHolder.setConnected(false)
        logLifecycle("cleaning up MediaSession")
        cleanupMediaSession()
        broadcastState()
    }

    private fun handleMetadata(metadata: MediaMetadata) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val pkg = activeController?.packageName ?: "unknown"
        updateActiveSessionPackageName()
        serviceStateHolder.setCurrentTrackTitle(title)
        serviceStateHolder.setCurrentTrackArtist(artist)

        logDebug("Metadata from $pkg: title=$title, artist=$artist, album=$album")

        if (title != null && (title != lastTrackInfo?.first || artist != lastTrackInfo?.second)) {
            lastTrackInfo = title to artist
            logDebug("New track detected: \"$title\" by \"$artist\" [from $pkg]")
            notifyManagerOfNewTrack(title, artist)
        }
    }

    private fun notifyManagerOfNewTrack(title: String, artist: String?) {
        serviceScope.launch {
            try {
                logDebug("Looking up volume for: $title")
                val trackVolume = trackVolumeRepository.getTrackVolume(title, artist)
                val offset = trackVolume?.volumeOffset ?: 1f

                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val absoluteOffset = (offset * maxVolume).roundToInt()
                val projectedVolume = (currentVolume + absoluteOffset).coerceIn(0, maxVolume)

                val hasFocus = audioManager.isMusicActive
                val isHeadset = isHeadsetFlow.first()
                logDebug("notifyManagerOfNewTrack: title=$title, artist=$artist, dbOffset=$offset, baseCandidate(currentVolume)=$currentVolume, projectedVolume=$projectedVolume/$maxVolume, isHeadset=$isHeadset, hasFocus=$hasFocus")

                manager.onAudioFocusChanged(hasFocus)
                manager.onTrackChanged(title, artist, offset, currentVolume, maxVolume)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying manager")
            }
        }
    }

    private fun cleanupMediaSession() {
        try {
            val hadController = activeController != null
            val hadSessionManager = sessionManager != null
            activeController?.unregisterCallback(metadataCallback)
            activeController = null
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            sessionManager = null
            logDebug(
                "complete (hadController=$hadController, hadSessionManager=$hadSessionManager)",
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: cleanupMediaSession FAILED")
        }
    }

    override fun onDestroy() {
        logLifecycle("START instanceId=$instanceId")
        serviceStateHolder.clearState()
        serviceStateHolder.setConnected(false)
        try {
            unregisterReceiver(volumeReceiver)
            logDebug("volumeReceiver unregistered")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "lifecycle: volumeReceiver was not registered")
        }
        cleanupMediaSession()
        manager.onServiceStopped()
        serviceScope.cancel()
        logLifecycle("serviceScope cancelled")
        broadcastState()
        super.onDestroy()
        logDebug("COMPLETE instanceId=$instanceId")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "CHANNEL_ID", "Volume Service Channel", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }


    private fun updateActiveSessionPackageName() {
        val newPackageName = activeController?.packageName
        serviceStateHolder.setActiveSessionPackageName(newPackageName)

        logDebug("Current activeSession: $newPackageName")
    }

    private suspend fun runIfAppInList(block: suspend () -> Unit) {
        val activeSessionPackageName =
            serviceStateHolder.state.value.activeSessionPackageName

        val isIncluded =
            selectedAppsInfoFlow
                .first()
                .map { it.packageName }
                .contains(activeSessionPackageName)
        if (isIncluded) {
            block()
        } else {
            logDebug("Функция пропущена: для $activeSessionPackageName не включено обучение")
        }
    }
}