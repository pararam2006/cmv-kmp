package com.pararam2006.cmv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pararam2006.cmv.core.Constants
import com.pararam2006.cmv.core.service.ListenerServiceStateHolder
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.domain.usecase.DeleteTrackVolumeUseCase
import com.pararam2006.cmv.domain.usecase.GetTrackVolumesUseCase
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.platform.SettingsPreferences
import com.pararam2006.cmv.platform.SystemService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(
    getTrackVolumesUseCase: GetTrackVolumesUseCase,
    private val saveTrackVolumeUseCase: SaveTrackVolumeUseCase,
    private val deleteTrackVolumeUseCase: DeleteTrackVolumeUseCase,
    private val systemService: SystemService,
    private val settingsPreferences: SettingsPreferences,
    private val serviceStateHolder: ListenerServiceStateHolder,
    headphonesDetector: HeadphonesRepository,
) : ViewModel() {

    private val _mainScreenUiState = MutableStateFlow(MainScreenUiState())
    private val trackDeleteOperations = mutableMapOf<Int, TrackDeleteOperation>()
    private var nextTrackDeleteOperationId = 0L

    private val tracksFlow = getTrackVolumesUseCase()
    val mainScreenUiState: StateFlow<MainScreenUiState> =
        combine(tracksFlow, _mainScreenUiState) { tracks, uiState ->
            val filteredTracks = if (uiState.searchQuery.isBlank()) {
                tracks.sortedByDescending { it.id }
            } else {
                tracks
                    .sortedByDescending { it.id }
                    .filter { track ->
                        track.trackTitle.contains(uiState.searchQuery, ignoreCase = true) ||
                                track.artistName?.contains(
                                    uiState.searchQuery,
                                    ignoreCase = true
                                ) == true
                    }
            }
            uiState.copy(tracks = filteredTracks, isTracksLoading = false)
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MainScreenUiState(),
            )

    private val isServiceSupported = systemService.isNotificationServiceSupported()
    private val notificationPermissionGranted = MutableStateFlow(
        !isServiceSupported || systemService.isNotificationServiceEnabled()
    )

    val listenerUiState =
        combine(
            serviceStateHolder.state,
            notificationPermissionGranted
        ) { state, permissionGranted ->
            ListenerUiState(
                serviceSupported = isServiceSupported,
                permissionGranted = permissionGranted,
                connected = state.isConnected,
                userStopped = state.userStopped,
                isStarting = state.isStarting,
                restartResult = state.restartResult,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ListenerUiState(
                serviceSupported = isServiceSupported,
                permissionGranted = notificationPermissionGranted.value,
                userStopped = settingsPreferences.getUserStopped(),
            ),
        )

    data class ListenerUiState(
        val serviceSupported: Boolean = true,
        val permissionGranted: Boolean = false,
        val connected: Boolean = false,
        val userStopped: Boolean = false,
        val isStarting: Boolean = false,
        val restartResult: Boolean? = null,
    ) {
        val isOn: Boolean get() = permissionGranted && connected && !userStopped
    }

    init {
        serviceStateHolder.setUserStopped(settingsPreferences.getUserStopped())

        viewModelScope.launch {
            serviceStateHolder.state.collect { serviceState ->
                _mainScreenUiState.update {
                    it.copy(
                        currentPlayingTrack = serviceState.currentTrackTitle,
                        currentPlayingArtist = serviceState.currentTrackArtist,
                    )
                }
            }
        }

        viewModelScope.launch {
            headphonesDetector.isHeadsetFlow.collect { isHeadset ->
                _mainScreenUiState.update { it.copy(isHeadset = isHeadset) }
                if (isServiceSupported && isHeadset == false) showHeadsetNotConnectedDialog()
                else closeHeadsetNotConnectedDialog()
            }
        }
    }

    fun openAddDialog() {
        _mainScreenUiState.update { it.copy(showAddDialog = true) }
    }

    fun closeAddDialog() {
        _mainScreenUiState.update {
            it.copy(
                showAddDialog = false,
                offsetToNewTrack = 0f,
            )
        }
    }

    fun startEdit(track: TrackVolume) {
        _mainScreenUiState.update {
            val editableTrack = if (track.offsetModel == com.pararam2006.cmv.domain.model.VolumeOffsetModel.DECIBEL) {
                track
            } else track.copy(volumeOffsetDb = 0f)
            it.copy(showEditDialog = true, dialogTrack = editableTrack)
        }
    }

    private var incrementJob: Job? = null
    fun startIncrementing() {
        incrementJob?.cancel()
        incrementJob = viewModelScope.launch {
            while (true) {
                val currentOffset = when {
                    _mainScreenUiState.value.showAddDialog -> _mainScreenUiState.value.offsetToNewTrack
                    _mainScreenUiState.value.showEditDialog -> _mainScreenUiState.value.dialogTrack?.volumeOffsetDb
                    else -> null
                }
                changeEditOffset(offset = (currentOffset ?: 0f) + Constants.DIALOG_OFFSET_STEP)
                delay(Constants.TINY_DELAY.milliseconds)
            }
        }
    }

    fun stopIncrementing() {
        incrementJob?.cancel(); incrementJob = null
    }

    private var serviceStartTimeoutJob: Job? = null

    private var decrementJob: Job? = null
    fun startDecrementing() {
        decrementJob?.cancel()
        decrementJob = viewModelScope.launch {
            while (true) {
                val currentOffset = when {
                    _mainScreenUiState.value.showAddDialog -> _mainScreenUiState.value.offsetToNewTrack
                    _mainScreenUiState.value.showEditDialog -> _mainScreenUiState.value.dialogTrack?.volumeOffsetDb
                    else -> null
                }
                changeEditOffset(offset = (currentOffset ?: 0f) - Constants.DIALOG_OFFSET_STEP)
                delay(Constants.TINY_DELAY.milliseconds)
            }
        }
    }

    fun stopDecrementing() {
        decrementJob?.cancel(); decrementJob = null
    }

    fun changeEditTitle(title: String) {
        _mainScreenUiState.update { it.copy(dialogTrack = it.dialogTrack?.copy(trackTitle = title)) }
    }

    fun changeEditArtist(artist: String?) {
        _mainScreenUiState.update { it.copy(dialogTrack = it.dialogTrack?.copy(artistName = artist)) }
    }

    fun changeEditOffset(offset: Float) {
        val boundedOffset = offset.coerceIn(Constants.VOLUME_SLIDER_VALUE_RANGE)
        _mainScreenUiState.update {
            when {
                it.showAddDialog -> it.copy(offsetToNewTrack = boundedOffset)
                it.showEditDialog -> it.copy(dialogTrack = it.dialogTrack?.copy(volumeOffsetDb = boundedOffset))
                else -> it
            }
        }
    }

    fun stopEdit() {
        _mainScreenUiState.update {
            it.copy(
                showEditDialog = false,
                dialogTrack = null,

                )
        }
    }

    fun startSearch() {
        _mainScreenUiState.update { it.copy(isTitleVisible = false) }
    }

    fun closeSearch() {
        _mainScreenUiState.update { it.copy(isTitleVisible = true, searchQuery = "") }
    }

    fun changeSearch(newSearch: String) {
        _mainScreenUiState.update { it.copy(searchQuery = newSearch) }
    }

    fun saveTrackVolume(title: String, artist: String?, offset: Float, id: Int = 0) {
        viewModelScope.launch {
            saveTrackVolumeUseCase(
                id = id,
                title = title,
                artist = artist,
                offsetDb = offset
            )
        }
    }

    fun startTrackDelete(id: Int) {
        if (id <= 0 || id in trackDeleteOperations) return

        val operationId = ++nextTrackDeleteOperationId
        val job = viewModelScope.launch {
            try {
                var elapsed = 0L
                while (elapsed < Constants.TRACK_DELETE_UNDO_TIMEOUT) {
                    val step = minOf(
                        Constants.TRACK_DELETE_PROGRESS_INTERVAL,
                        Constants.TRACK_DELETE_UNDO_TIMEOUT - elapsed,
                    )
                    delay(step.milliseconds)
                    elapsed += step
                    val progress =
                        (1f - elapsed.toFloat() / Constants.TRACK_DELETE_UNDO_TIMEOUT)
                            .coerceIn(0f, 1f)
                    updateTrackDeletionProgress(id, progress)
                }
                deleteTrackVolumeUseCase(id)
            } finally {
                finishTrackDelete(id, operationId)
            }
        }

        trackDeleteOperations[id] = TrackDeleteOperation(operationId, job)
        updateTrackDeletionProgress(id, 1f)
    }

    fun cancelTrackDelete(id: Int) {
        val operation = trackDeleteOperations.remove(id) ?: return
        operation.job.cancel()
        removeTrackDeletionProgress(id)
    }

    private fun updateTrackDeletionProgress(id: Int, progress: Float) {
        _mainScreenUiState.update { state ->
            if (id !in trackDeleteOperations) {
                state
            } else {
                state.copy(
                    trackDeletionProgress =
                        state.trackDeletionProgress + (id to progress)
                )
            }
        }
    }

    private fun finishTrackDelete(id: Int, operationId: Long) {
        if (trackDeleteOperations[id]?.id != operationId) return
        trackDeleteOperations.remove(id)
        removeTrackDeletionProgress(id)
    }

    private fun removeTrackDeletionProgress(id: Int) {
        _mainScreenUiState.update { state ->
            if (id !in state.trackDeletionProgress) {
                state
            } else {
                state.copy(trackDeletionProgress = state.trackDeletionProgress - id)
            }
        }
    }

    private data class TrackDeleteOperation(
        val id: Long,
        val job: Job,
    )

    fun searchTrackInBrowser(query: String) {
        systemService.searchWeb(query)
    }

    fun refreshListenerUiState() {
        notificationPermissionGranted.value =
            !isServiceSupported || systemService.isNotificationServiceEnabled()
    }

    fun toggleService(): Boolean {
        if (serviceStateHolder.state.value.isStarting) return true

        refreshListenerUiState()
        val permissionGranted = notificationPermissionGranted.value
        val serviceState = serviceStateHolder.state.value
        val currentlyOn = permissionGranted && serviceState.isConnected && !serviceState.userStopped
        if (!currentlyOn && !permissionGranted) {
            openNotificationsAccessSettings()
            return true
        }

        val userStopped = currentlyOn
        settingsPreferences.setUserStopped(userStopped)
        serviceStateHolder.setUserStopped(userStopped)
        serviceStateHolder.setRestartResult(null)
        serviceStateHolder.setStarting(!currentlyOn)

        val requestAccepted = systemService.toggleService(currentlyOn)
        if (!requestAccepted) {
            settingsPreferences.setUserStopped(!userStopped)
            serviceStateHolder.setUserStopped(!userStopped)
            serviceStateHolder.setStarting(false)
            serviceStateHolder.setRestartResult(false)
            return false
        }

        serviceStartTimeoutJob?.cancel()
        if (!currentlyOn) {
            serviceStartTimeoutJob = viewModelScope.launch {
                delay(Constants.LAUNCHING_TIMEOUT.milliseconds)
                if (serviceStateHolder.state.value.isStarting) {
                    serviceStateHolder.setStarting(false)
                    serviceStateHolder.setRestartResult(false)
                }
            }
        }

        return true
    }

    fun openNotificationsAccessSettings() {
        systemService.openNotificationSettings()
    }

    fun clearRestartResult() {
        serviceStateHolder.setRestartResult(null)
    }

    fun showHeadsetNotConnectedDialog() {
        _mainScreenUiState.update { it.copy(showHeadsetNotConnectedDialog = true) }
    }

    fun closeHeadsetNotConnectedDialog() {
        _mainScreenUiState.update { it.copy(showHeadsetNotConnectedDialog = false) }
    }
}
