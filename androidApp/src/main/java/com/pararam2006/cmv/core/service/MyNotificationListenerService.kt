package com.pararam2006.cmv.core.service

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
import com.pararam2006.cmv.core.Constants.LAUNCHING_TIMEOUT
import com.pararam2006.cmv.core.Constants.SMALL_DELAY
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.platform.AndroidSystemVolumeAdapter
import com.pararam2006.cmv.domain.service.PlaybackTrackingCoordinator
import com.pararam2006.cmv.platform.PlaybackRuntimeState
import com.pararam2006.cmv.platform.PlaybackRuntimeStatus
import com.pararam2006.cmv.platform.SettingsPreferences
import com.pararam2006.cmv.utils.logDebug
import com.pararam2006.cmv.utils.logLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class MyNotificationListenerService : NotificationListenerService(), KoinComponent {
    private val serviceStateHolder: ListenerServiceStateHolder by inject()
    private val playbackCoordinator: PlaybackTrackingCoordinator by inject()
    private val settingsPreferences: SettingsPreferences by inject()
    private val headphonesDetector: HeadphonesRepository by inject()
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val systemVolumeAdapter by lazy { AndroidSystemVolumeAdapter(audioManager) }
    private val selectedAppsInfoFlow by lazy { playbackCoordinator.selectedApps }
    private val isHeadsetFlow by lazy { headphonesDetector.isHeadsetFlow }
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var rebindJob: Job? = null
    @Volatile
    private var selectedPackageNames: Set<String> = emptySet()

    companion object {
        private const val TAG = "CMV.Service"

        const val ACTION_START: String = "com.pararam2006.cmv.ACTION_START_LISTENER"
        const val ACTION_STOP: String = "com.pararam2006.cmv.ACTION_STOP_LISTENER"
        const val ACTION_STATE_CHANGED: String = "com.pararam2006.cmv.ACTION_LISTENER_STATE_CHANGED"
        const val EXTRA_CONNECTED: String = "extra_connected"
        const val EXTRA_USER_STOPPED: String = "extra_user_stopped"
    }

    private val instanceId = hashCode()

    private fun startStateCollectors() {
        logLifecycle("state collectors starting: instanceId=$instanceId")

        serviceScope.launch {
            logLifecycle("selectedAppsInfoFlow started")
            selectedAppsInfoFlow.collect { newList ->
                logDebug("selectedApps count=${newList.size}")
                selectedPackageNames = newList.mapTo(mutableSetOf()) { it.packageName }
                serviceStateHolder.setSelectedApps(newList)
                if (serviceStateHolder.state.value.isConnected) {
                    val componentName = ComponentName(
                        this@MyNotificationListenerService,
                        MyNotificationListenerService::class.java,
                    )
                    runCatching { sessionManager?.getActiveSessions(componentName) }
                        .getOrNull()
                        ?.let(sessionsChangedListener::onActiveSessionsChanged)
                }
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
            logLifecycle("isHeadsetFlow started")
            isHeadsetFlow.collect { isHeadset ->
                logDebug("isHeadset=$isHeadset")
                val route = systemVolumeAdapter.routeSnapshot()?.copy(isHeadphones = isHeadset)
                serviceStateHolder.setAudioRoute(route)
                playbackCoordinator.onHeadsetStateChanged(isHeadset)
                updateForegroundNotification(isHeadset)
            }
        }
    }

    private fun rebindNotificationListener() {
        rebindJob?.cancel()
        rebindJob = serviceScope.launch {
            val componentName = ComponentName(
                this@MyNotificationListenerService,
                MyNotificationListenerService::class.java,
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    NotificationListenerService.requestUnbind(componentName)
                    logLifecycle("requestUnbind(component) called before rebind")
                    delay(SMALL_DELAY.milliseconds)
                }

                repeat(2) { attempt ->
                    if (serviceStateHolder.state.value.isConnected) return@launch

                    NotificationListenerService.requestRebind(componentName)
                    logLifecycle("requestRebind() called, attempt=" + (attempt + 1))
                    delay((LAUNCHING_TIMEOUT / 2).milliseconds)
                }

                if (!serviceStateHolder.state.value.isConnected) {
                    serviceStateHolder.setStarting(false)
                    serviceStateHolder.setRestartResult(false)
                    serviceStateHolder.setRuntimeState(
                        PlaybackRuntimeState(
                            PlaybackRuntimeStatus.ERROR,
                            "Notification listener rebind timed out",
                        ),
                    )
                    logLifecycle("listener rebind timed out")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                serviceStateHolder.setStarting(false)
                serviceStateHolder.setRestartResult(false)
                serviceStateHolder.setRuntimeState(
                    PlaybackRuntimeState(PlaybackRuntimeStatus.ERROR, e.message),
                )
                Timber.tag(TAG).e(e, "lifecycle: listener rebind failed")
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return

            val systemVolume = systemVolumeAdapter.snapshot()
            serviceStateHolder.setSystemVolume(systemVolume)
            val isHeadset = headphonesDetector.computeIsHeadsetConnected()
            serviceStateHolder.setAudioRoute(
                systemVolumeAdapter.routeSnapshot()?.copy(isHeadphones = isHeadset),
            )
            val hasFocus = audioManager.isMusicActive
            playbackCoordinator.onVolumeChanged(
                systemVolume = systemVolume,
                isHeadset = isHeadset,
                hasAudioFocus = hasFocus,
            )
            logDebug(
                "VOLUME_CHANGED_ACTION: volume=${systemVolume.currentVolumeDb}dB " +
                    "(${systemVolume.currentVolume}/${systemVolume.maxVolume}), " +
                    "isHeadset=$isHeadset, hasFocus=$hasFocus",
            )
        }
    }

    // Callback for metadata changes on the active media controller
    private val metadataCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            logDebug("MediaController.onMetadataChanged()")
            if (metadata == null) {
                clearCurrentTrackState()
                playbackCoordinator.onSessionDetached()
            } else {
                handleMetadata(metadata)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            logDebug("MediaController.onPlaybackStateChanged(), isPlaying=$isPlaying")
            playbackCoordinator.onPlaybackStateChanged(isPlaying)
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
            val controller = controllers?.firstOrNull { it.packageName in selectedPackageNames }
            if (controller != null) {
                logDebug("Attaching to controller: ${controller.packageName}")
                activeController = controller
                updateActiveSessionPackageName()
                controller.registerCallback(metadataCallback)

                // Also process current metadata immediately
                val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                playbackCoordinator.onPlaybackStateChanged(isPlaying)
                controller.metadata?.let { handleMetadata(it) }
            } else {
                updateActiveSessionPackageName()
                clearCurrentTrackState()
                playbackCoordinator.onSessionDetached()
                logDebug("No selected active media sessions")
            }
        }

    private fun updateForegroundNotification(isHeadset: Boolean) {
        val stopIntent = Intent(this, MyNotificationListenerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (isHeadset) "Умный режим включён" else "Спит (наушники отключены)"

        val notification =
            NotificationCompat.Builder(this, "CHANNEL_ID")
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
            val notificationManager = getSystemService(NotificationManager::class.java)
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
        super.onCreate()
        serviceStateHolder.setRuntimeState(PlaybackRuntimeState(PlaybackRuntimeStatus.STARTING))

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
            serviceStateHolder.setSystemVolume(systemVolumeAdapter.snapshot())
            serviceStateHolder.setAudioRoute(
                systemVolumeAdapter.routeSnapshot()?.copy(isHeadphones = isHeadset),
            )
            val text = if (isHeadset) "Приложение активно" else "Спит (наушники отключены)"

            val notification =
                NotificationCompat.Builder(this, "CHANNEL_ID").setContentTitle("CMV Service")
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .addAction(R.drawable.ic_launcher_foreground, "Выключить", stopPendingIntent)
                    .setOngoing(true).build()

            startForeground(1, notification)
            logDebug("foreground started, isHeadset=$isHeadset")

            // Start flows only after the service is fully created and its notification channel exists.
            startStateCollectors()

            // Register Volume Receiver
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(volumeReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(volumeReceiver, filter)
            }
            logLifecycle("volumeReceiver registered")

            // Apply only the latest command and revalidate it after the OEM delay.
            serviceScope.launch {
                logLifecycle("volumeCommands started")
                playbackCoordinator.volumeCommands.collectLatest { command ->
                    delay(SMALL_DELAY.milliseconds)
                    if (!playbackCoordinator.isVolumeCommandCurrent(command)) {
                        logDebug(
                            "Stale volume command skipped: target=${command.targetVolumeDb}dB, " +
                                "generation=${command.trackGeneration}",
                        )
                        return@collectLatest
                    }

                    val beforeVolume = systemVolumeAdapter.snapshot()
                    val targetNativeVolume = systemVolumeAdapter.nativeVolumeForDb(command.targetVolumeDb)
                    val shouldShowSystemUi = settingsPreferences.isSystemVolumeUiEnabled()
                    val volumeFlags = if (shouldShowSystemUi) AudioManager.FLAG_SHOW_UI else 0
                    logDebug(
                        "Manager requested volume change: before=${beforeVolume.currentVolumeDb}dB, " +
                            "target=${command.targetVolumeDb}dB/native=$targetNativeVolume, " +
                            "showSystemUi=$shouldShowSystemUi",
                    )
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        targetNativeVolume,
                        volumeFlags,
                    )
                    val afterVolume = systemVolumeAdapter.snapshot()
                    logDebug(
                        "Volume applied: after=${afterVolume.currentVolumeDb}dB, " +
                            "target=${command.targetVolumeDb}dB",
                    )
                }
            }

            serviceScope.launch {
                logLifecycle("playbackCoordinator.debugState started")
                playbackCoordinator.debugState.collect { st ->
                    Timber.tag(TAG).v(
                        "managerState: track=${st.currentTrackTitle}/${st.currentTrackArtist}, " +
                                "base=${st.baseVolumeDb}dB, offset=${st.currentLearnedOffsetDb}dB, " +
                                "expectedVol=${st.expectedProgrammaticVolumeDb}dB, headset=${st.isHeadsetConnected}, " +
                                "focus=${st.hasAudioFocus}, playing=${st.isPlaying}, playingMs=${st.accumulatedPlayingTimeMs}"
                    )
                }
            }
            logDebug("COMPLETE instanceId=$instanceId")
        } catch (e: Exception) {
            serviceStateHolder.setRuntimeState(
                PlaybackRuntimeState(PlaybackRuntimeStatus.ERROR, e.message),
            )
            Timber.tag(TAG).e(e, "lifecycle: onCreate FAILED during foreground setup")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logDebug("action=${intent?.action}, flags=$flags, startId=$startId")
        if (intent?.action == ACTION_START) {
            logLifecycle("ACTION_START — requesting notification-listener rebind")
            settingsPreferences.setUserStopped(false)
            serviceStateHolder.setUserStopped(false)
            serviceStateHolder.setRuntimeState(PlaybackRuntimeState(PlaybackRuntimeStatus.STARTING))
            rebindNotificationListener()
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            logLifecycle("ACTION_STOP — unbind and remove foreground")
            rebindJob?.cancel()
            rebindJob = null
            serviceStateHolder.setStarting(false)
            serviceStateHolder.setRuntimeState(PlaybackRuntimeState(PlaybackRuntimeStatus.STOPPED))
            try {
                settingsPreferences.setUserStopped(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist userStopped=true")
            }
            broadcastState()

            try {
                // NotificationListenerService is OS-managed; disconnect without revoking access.
                requestUnbind()
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
        if (settingsPreferences.getUserStopped()) {
            logLifecycle("onListenerConnected ignored because the user stopped the service")
            serviceStateHolder.setConnected(false)
            serviceStateHolder.setStarting(false)
            serviceStateHolder.setRuntimeState(PlaybackRuntimeState(PlaybackRuntimeStatus.STOPPED))
            broadcastState()
            runCatching { requestUnbind() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val wasStarting = serviceStateHolder.state.value.isStarting
        rebindJob?.cancel()
        rebindJob = null
        serviceStateHolder.setConnected(true)
        serviceStateHolder.setStarting(false)
        serviceStateHolder.setRuntimeState(PlaybackRuntimeState(PlaybackRuntimeStatus.RUNNING))
        if (wasStarting) serviceStateHolder.setRestartResult(true)
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
                logDebug("Active session: ${c.packageName}")
            }

            val controller = activeSessions?.firstOrNull { it.packageName in selectedPackageNames }
            if (controller != null) {
                logDebug("Attaching to current controller: ${controller.packageName}")
                activeController = controller
                updateActiveSessionPackageName()
                controller.registerCallback(metadataCallback)

                playbackCoordinator.resetCurrentTrack()
                val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                playbackCoordinator.onPlaybackStateChanged(isPlaying)
                logDebug("onListenerConnected: current track reset to force volume re-apply")

                controller.metadata?.let { handleMetadata(it) }
            }
            else {
                updateActiveSessionPackageName()
                clearCurrentTrackState()
                playbackCoordinator.onSessionDetached()
                logDebug("No selected active media sessions on connect")
            }
        } catch (e: Exception) {
            serviceStateHolder.setRuntimeState(
                PlaybackRuntimeState(PlaybackRuntimeStatus.ERROR, e.message),
            )
            Timber.tag(TAG).e(e, "lifecycle: onListenerConnected FAILED")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        serviceStateHolder.setConnected(false)
        serviceStateHolder.setRuntimeState(
            if (serviceStateHolder.state.value.userStopped) {
                PlaybackRuntimeState(PlaybackRuntimeStatus.STOPPED)
            } else {
                PlaybackRuntimeState(
                    PlaybackRuntimeStatus.ERROR,
                    "Notification listener disconnected",
                )
            },
        )
        if (rebindJob?.isActive != true) {
            serviceStateHolder.setStarting(false)
        }
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

        val systemVolume = systemVolumeAdapter.snapshot()
        serviceStateHolder.setSystemVolume(systemVolume)
        val isHeadset = headphonesDetector.computeIsHeadsetConnected()
        serviceStateHolder.setAudioRoute(
            systemVolumeAdapter.routeSnapshot()?.copy(isHeadphones = isHeadset),
        )
        val hasFocus = audioManager.isMusicActive
        playbackCoordinator.onTrackMetadataChanged(
            title = title,
            artist = artist,
            systemVolume = systemVolume,
            hasAudioFocus = hasFocus,
        )
        logDebug(
            "Metadata queued from $pkg: title=$title, artist=$artist, album=$album, " +
                "volume=${systemVolume.currentVolumeDb}dB " +
                "(${systemVolume.currentVolume}/${systemVolume.maxVolume}), focus=$hasFocus",
        )
    }

    private fun clearCurrentTrackState() {
        serviceStateHolder.setCurrentTrackTitle(null)
        serviceStateHolder.setCurrentTrackArtist(null)
    }

    private fun cleanupMediaSession() {
        try {
            val hadController = activeController != null
            val hadSessionManager = sessionManager != null
            activeController?.unregisterCallback(metadataCallback)
            activeController = null
            updateActiveSessionPackageName()
            clearCurrentTrackState()
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
        playbackCoordinator.onServiceStopped()
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
        playbackCoordinator.onActiveSessionPackageNameChanged(newPackageName)

        logDebug("Current activeSession: $newPackageName")
    }

}
