package com.pararam2006.cmv.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pararam2006.cmv.core.service.ListenerServiceStateHolder
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.domain.service.PlaybackTrackingCoordinator
import com.pararam2006.cmv.platform.SettingsPreferences
import com.pararam2006.cmv.platform.SystemService
import com.pararam2006.cmv.platform.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class DebugScreenViewModel(
    private val serviceStateHolder: ListenerServiceStateHolder,
    private val playbackCoordinator: PlaybackTrackingCoordinator,
    private val settingsPreferences: SettingsPreferences,
    private val systemService: SystemService,
    private val headphonesRepository: HeadphonesRepository,
) : ViewModel() {
    private val clock = flow {
        while (true) {
            emit(currentTimeMillis())
            delay(UPDATE_INTERVAL_MS)
        }
    }

    val uiState = combine(
        serviceStateHolder.state,
        playbackCoordinator.debugState,
        playbackCoordinator.currentTrack,
        headphonesRepository.isHeadsetFlow.onStart {
            emit(runCatching(headphonesRepository::computeIsHeadsetConnected).getOrDefault(false))
        },
        clock,
    ) { serviceState, volumeState, coordinatorTrack, detectorSeesHeadphones, now ->
        DebugScreenUiState(
            serviceState = serviceState,
            volumeState = volumeState,
            coordinatorTrack = coordinatorTrack,
            detectorSeesHeadphones = detectorSeesHeadphones,
            serviceSupported = systemService.isNotificationServiceSupported(),
            notificationPermissionGranted = systemService.isNotificationServiceEnabled(),
            appMode = settingsPreferences.appMode,
            learningTimeSeconds = settingsPreferences.learningTimeSeconds,
            showSystemVolumeUi = settingsPreferences.showSystemVolumeUi,
            observedAtMs = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DebugScreenUiState(
            serviceState = serviceStateHolder.state.value,
            volumeState = playbackCoordinator.debugState.value,
            coordinatorTrack = playbackCoordinator.currentTrack.value,
            detectorSeesHeadphones = runCatching {
                headphonesRepository.computeIsHeadsetConnected()
            }.getOrNull(),
            serviceSupported = systemService.isNotificationServiceSupported(),
            notificationPermissionGranted = systemService.isNotificationServiceEnabled(),
            appMode = settingsPreferences.appMode,
            learningTimeSeconds = settingsPreferences.learningTimeSeconds,
            showSystemVolumeUi = settingsPreferences.showSystemVolumeUi,
            observedAtMs = currentTimeMillis(),
        ),
    )

    private companion object {
        const val UPDATE_INTERVAL_MS = 500L
    }
}
