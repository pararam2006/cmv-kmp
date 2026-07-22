package com.pararam2006.cmv.ui.main

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pararam2006.cmv.core.Constants
import com.pararam2006.cmv.core.Constants.SMALL_DELAY
import com.pararam2006.cmv.core.service.MyNotificationListenerService
import com.pararam2006.cmv.core.service.MyNotificationListenerServiceStateHolder
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.domain.usecase.DeleteTrackVolumeUseCase
import com.pararam2006.cmv.domain.usecase.GetTrackVolumesUseCase
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.utils.SettingsPreferences
import com.pararam2006.cmv.utils.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel(
    getTrackVolumesUseCase: GetTrackVolumesUseCase,
    private val saveTrackVolumeUseCase: SaveTrackVolumeUseCase,
    private val deleteTrackVolumeUseCase: DeleteTrackVolumeUseCase,
    private val settingsPreferences: SettingsPreferences,
    private val context: Application,
    private val serviceStateHolder: MyNotificationListenerServiceStateHolder,
    headphonesDetector: HeadphonesRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "CMV.MainVM"
        private fun lifecycle(event: String, detail: String = "") {
            val message =
                if (detail.isEmpty()) "lifecycle: $event" else "lifecycle: $event — $detail"
            Timber.tag(TAG).d(message)
        }
    }

    private val _mainScreenUiState = MutableStateFlow(MainScreenUiState())

    private val tracksFlow = getTrackVolumesUseCase()
    val mainScreenUiState: StateFlow<MainScreenUiState> =
        combine(tracksFlow, _mainScreenUiState) { tracks, uiState ->
            // tracks always come from DB flow; UI events should not be able to overwrite them.
            val filteredTracks = if (uiState.searchQuery.isBlank()) {
                tracks.sortedByDescending { it.id }
            } else {
                tracks
                    .sortedByDescending { it.id }
                    .filter { track ->
                        track.trackTitle.contains(
                            uiState.searchQuery,
                            ignoreCase = true
                        ) || track.artistName?.contains(
                            uiState.searchQuery,
                            ignoreCase = true
                        ) == true
                    }
            }
            uiState.copy(
                tracks = filteredTracks,
                isTracksLoading = false
            )
        }
//            .debounce(300)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MainScreenUiState(),
            )

    data class ListenerUiState(
        val permissionGranted: Boolean = false,
        val connected: Boolean = false,
        val userStopped: Boolean = false,
        val isStarting: Boolean = false,
        val restartResult: Boolean? = null,
    ) {
        val isOn: Boolean get() = permissionGranted && connected && !userStopped
    }

    private val _listenerUiState = MutableStateFlow(ListenerUiState())
    val listenerUiState: StateFlow<ListenerUiState> = _listenerUiState.asStateFlow()

    private val stateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != MyNotificationListenerService.ACTION_STATE_CHANGED) return
            val connected =
                intent.getBooleanExtra(MyNotificationListenerService.EXTRA_CONNECTED, false)
            val userStopped =
                intent.getBooleanExtra(MyNotificationListenerService.EXTRA_USER_STOPPED, false)
            persistUserStopped(userStopped)
            serviceStateHolder.setConnected(connected)
            serviceStateHolder.setUserStopped(userStopped)
            lifecycle(
                "stateReceiver",
                "connected=$connected, userStopped=$userStopped, permission=${isNotificationServiceEnabled()}",
            )
            _listenerUiState.update {
                it.copy(
                    connected = connected,
                    userStopped = userStopped,
                    permissionGranted = isNotificationServiceEnabled(),
                    isStarting = if (connected || userStopped || !isNotificationServiceEnabled()) false
                    else _listenerUiState.value.isStarting,
                )
            }
        }
    }

    private fun persistUserStopped(value: Boolean) {
        try {
            settingsPreferences.setUserStopped(value)
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist userStopped=$value")
        }
    }

    private val isHeadsetFlow = headphonesDetector.isHeadsetFlow

    init {
        lifecycle("init", "instanceId=${hashCode()}")
        // Listen for service state changes to update UI immediately.
        try {
            ContextCompat.registerReceiver(
                context,
                stateReceiver,
                IntentFilter(MyNotificationListenerService.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            lifecycle(
                "init",
                "stateReceiver registered for ${MyNotificationListenerService.ACTION_STATE_CHANGED}"
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "lifecycle: failed to register stateReceiver")
        }

        viewModelScope.launch {
            serviceStateHolder.state.collect { serviceState ->
                _listenerUiState.update {
                    it.copy(
                        connected = serviceState.isConnected,
                        userStopped = serviceState.userStopped,
                    )
                }

                _mainScreenUiState.update {
                    it.copy(
                        currentPlayingTrack = serviceState.currentTrackTitle,
                        currentPlayingArtist = serviceState.currentTrackArtist,
                    )
                }
            }
        }

        refreshListenerUiState()

        // Safety timeout: if service doesn't connect on startup, don't show spinner forever.
        viewModelScope.launch {
            delay(Constants.LAUNCHING_TIMEOUT)
            if (_listenerUiState.value.isStarting) {
                _listenerUiState.update {
                    it.copy(isStarting = false)
                }
            }
        }

        viewModelScope.launch {
            isHeadsetFlow.collect { isHeadset ->
                _mainScreenUiState.update {
                    it.copy(
                        isHeadset = isHeadset,
                    )
                }

                if (!isHeadset) {
                    showHeadsetNotConnectedDialog()
                } else {
                    closeHeadsetNotConnectedDialog()
                }
            }
        }

        if (_mainScreenUiState.value.isHeadset) {
            showHeadsetNotConnectedDialog()
        }
    }

    fun openAddDialog() {
        _mainScreenUiState.update { it.copy(showAddDialog = true) }
    }

    fun closeAddDialog() {
        _mainScreenUiState.update {
            it.copy(
                showAddDialog = false
            )
        }
    }

    fun startEdit(track: TrackVolume) {
        _mainScreenUiState.update {
            it.copy(
                showEditDialog = true,
                dialogTrack = track
            )
        }
    }

    private var incrementJob: Job? = null

    fun startIncrementing() {
        incrementJob?.cancel()
        incrementJob = viewModelScope.launch {
            while (isActive) {
                val currentOffset = when {
                    _mainScreenUiState.value.showAddDialog -> {
                        _mainScreenUiState.value.offsetToNewTrack
                    }

                    _mainScreenUiState.value.showEditDialog -> {
                        _mainScreenUiState.value.dialogTrack?.volumeOffset
                    }

                    else -> null
                }
                changeEditOffset(offset = (currentOffset ?: 1f) + Constants.DIALOG_OFFSET_STEP)
                delay(Constants.TINY_DELAY)
            }
        }
    }

    fun stopIncrementing() {
        incrementJob?.cancel()
        incrementJob = null
    }

    private var decrementJob: Job? = null

    fun startDecrementing() {
        decrementJob?.cancel()
        decrementJob = viewModelScope.launch {
            while (isActive) {
                val currentOffset = when {
                    _mainScreenUiState.value.showAddDialog -> {
                        _mainScreenUiState.value.offsetToNewTrack
                    }

                    _mainScreenUiState.value.showEditDialog -> {
                        _mainScreenUiState.value.dialogTrack?.volumeOffset
                    }

                    else -> null
                }
                changeEditOffset(offset = (currentOffset ?: 1f) - Constants.DIALOG_OFFSET_STEP)
                delay(Constants.TINY_DELAY)
            }
        }
    }

    fun stopDecrementing() {
        decrementJob?.cancel()
        decrementJob = null
    }

    fun changeEditTitle(title: String) {
        _mainScreenUiState.update {
            it.copy(
                dialogTrack = it.dialogTrack?.copy(
                    trackTitle = title
                )
            )
        }
    }

    fun changeEditArtist(artist: String?) {
        _mainScreenUiState.update {
            it.copy(
                dialogTrack = it.dialogTrack?.copy(
                    artistName = artist
                )
            )
        }
    }

    fun changeEditOffset(offset: Float) {
        _mainScreenUiState.update {
            when {
                _mainScreenUiState.value.showAddDialog -> {
                    it.copy(
                        offsetToNewTrack = offset
                    )
                }

                _mainScreenUiState.value.showEditDialog -> {

                    it.copy(
                        dialogTrack = it.dialogTrack?.copy(
                            volumeOffset = offset
                        )
                    )
                }

                else -> return
            }
        }
    }

    fun stopEdit() {
        _mainScreenUiState.update {
            it.copy(
                showEditDialog = false,
                dialogTrack = null
            )
        }
    }

    fun startSearch() {
        _mainScreenUiState.update {
            it.copy(
                isTitleVisible = false,
            )
        }
    }

    fun closeSearch() {
        _mainScreenUiState.update {
            it.copy(
                isTitleVisible = true,
                searchQuery = "",
            )
        }
    }

    fun changeSearch(newSearch: String) {
        _mainScreenUiState.update {
            it.copy(
                searchQuery = newSearch,
            )
        }
    }

    fun saveTrackVolume(title: String, artist: String?, offset: Float, id: Int = 0) {
        viewModelScope.launch {
            saveTrackVolumeUseCase(
                id = id,
                title = title,
                artist = artist,
                offset = offset,
            )
        }
    }

    fun deleteTrackVolume(id: Int) {
        viewModelScope.launch {
            deleteTrackVolumeUseCase(id)
        }
    }

    fun searchTrackInBrowser(query: String) {
        val intent =
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, query)
                addFlags(FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    fun isNotificationServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )
        val component = ComponentName(context, MyNotificationListenerService::class.java)
        // enabled_notification_listeners contains flattened component names (not just package names)
        return enabled?.contains(component.flattenToString()) == true
    }

    fun isServiceActuallyConnected(): Boolean {
        return serviceStateHolder.state.value.isConnected
    }

    private fun isComponentEnabled(): Boolean {
        val componentName = ComponentName(context, MyNotificationListenerService::class.java)
        val state = context.packageManager.getComponentEnabledSetting(componentName)
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    /**
     * "Enabled" here means: user granted Notification Access AND our component is enabled AND
     * the listener is currently connected AND user didn't stop it from our UI.
     */
    fun isListenerEnabledAndRunning(): Boolean {
        return isNotificationServiceEnabled() &&
                isComponentEnabled() &&
                isServiceActuallyConnected() &&
                !serviceStateHolder.state.value.userStopped
    }

    fun refreshListenerUiState() {
        _listenerUiState.update {
            it.copy(
                permissionGranted = isNotificationServiceEnabled(),
                connected = isServiceActuallyConnected(),
                userStopped = serviceStateHolder.state.value.userStopped,
                isStarting = if (isServiceActuallyConnected() || serviceStateHolder.state.value.userStopped || !isNotificationServiceEnabled()) false
                else _listenerUiState.value.isStarting,
            )
        }
    }

    /**
     * Manually restart the service after user stopped it.
     * Clears the userStopped flag and re-enables the component.
     */
    fun restartService() {
        logDebug("Manual restart requested by user")
        persistUserStopped(false)
        refreshListenerUiState()
        _listenerUiState.update {
            it.copy(restartResult = null)
        }

        // If user disabled Notification Access, we cannot start NotificationListenerService programmatically.
        // The only valid way is to send the user to the system settings screen.
        if (!isNotificationServiceEnabled()) {
            Timber.w("Notification Access is disabled — opening settings screen")
            openNotificationsAccessSettings()
            _listenerUiState.update {
                it.copy(restartResult = false)
            }
            return
        }

        val componentName = ComponentName(context, MyNotificationListenerService::class.java)

        // NotificationListenerService is managed by the OS; rebind is "best effort".
        // Avoid component toggling here to prevent "permission drops" on some OEM ROMs.
        viewModelScope.launch(Dispatchers.Main.immediate) {
            // Block the UI while we're waiting for the system to actually connect the listener.
            _listenerUiState.update {
                it.copy(
                    isStarting = true
                )
            }

            delay(SMALL_DELAY)
            try {
                NotificationListenerService.requestRebind(componentName)
                Timber.d("Restart: requestRebind() called (waiting for onListenerConnected)")
            } catch (e: Exception) {
                Timber.e(e, "Restart: requestRebind() failed")
            }

            // Safety timeout: on some ROMs (MIUI) rebind can be delayed/ignored.
            delay(Constants.LAUNCHING_TIMEOUT)
            if (_listenerUiState.value.isStarting) {
                val actuallyConnected = isServiceActuallyConnected()
                _listenerUiState.update {
                    it.copy(
                        isStarting = false,
                        restartResult = actuallyConnected, // true/false по факту!
                    )
                }
            }
        }
    }

    /**
     * Toggle "listening" state for NotificationListenerService.
     * - If currently enabled+running: send STOP action (disables component via service code).
     * - If off: if permission disabled -> open settings; else -> restart (rebind).
     */
    fun toggleService(): Boolean {
        if (_listenerUiState.value.isStarting) return false

        return try {
            if (isListenerEnabledAndRunning()) {
                Timber.d("Toggle: service is ON — sending STOP action")
                persistUserStopped(true)
                val stopIntent = Intent(context, MyNotificationListenerService::class.java).apply {
                    action = MyNotificationListenerService.ACTION_STOP
                }
                ContextCompat.startForegroundService(context, stopIntent)
                refreshListenerUiState()
                true  // Успех
            } else {
                Timber.d("Toggle: service is OFF — attempting to turn ON")
                restartService()
                true // Успех
            }
        } catch (e: Exception) {
            Timber.e(e, "Toggle failed")
            false  // Показ ListenerError
        }
    }

    fun clearRestartResult() {
        _listenerUiState.update {
            it.copy(restartResult = null)
        }
    }

    fun openNotificationsAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun showHeadsetNotConnectedDialog() {
        _mainScreenUiState.update {
            it.copy(showHeadsetNotConnectedDialog = true)
        }
    }

    fun closeHeadsetNotConnectedDialog() {
        _mainScreenUiState.update {
            it.copy(showHeadsetNotConnectedDialog = false)
        }
    }

    override fun onCleared() {
        lifecycle("onCleared", "unregistering stateReceiver")
        try {
            context.unregisterReceiver(stateReceiver)
            lifecycle("onCleared", "stateReceiver unregistered")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "lifecycle: stateReceiver was not registered")
        }
        super.onCleared()
    }
}