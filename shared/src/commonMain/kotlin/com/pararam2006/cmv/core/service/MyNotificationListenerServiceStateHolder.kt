package com.pararam2006.cmv.core.service

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.platform.AudioRouteSnapshot
import com.pararam2006.cmv.platform.PlaybackRuntimeState
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface ListenerServiceStateHolder {
    val state: StateFlow<MyNotificationListenerServiceState>

    fun setConnected(isConnected: Boolean)
    fun setUserStopped(userStopped: Boolean)
    fun setSelectedApps(selectedApps: List<AppInfo>)
    fun setActiveSessionPackageName(activeSessionPackageName: String?)
    fun setCurrentTrackTitle(new: String?)
    fun setCurrentTrackArtist(new: String?)
    fun setAudioRoute(route: AudioRouteSnapshot?)
    fun setSystemVolume(volume: SystemVolumeSnapshot?)
    fun setRuntimeState(state: PlaybackRuntimeState)
    fun setStarting(isStarting: Boolean)
    fun setRestartResult(result: Boolean?)
    fun clearState()
}

class MyNotificationListenerServiceStateHolder : ListenerServiceStateHolder {
    private val _myNotificationListenerServiceState =
        MutableStateFlow(MyNotificationListenerServiceState())
    override val state: StateFlow<MyNotificationListenerServiceState> =
        _myNotificationListenerServiceState.asStateFlow()

    override fun setConnected(isConnected: Boolean) {
        _myNotificationListenerServiceState.update { it.copy(isConnected = isConnected) }
    }

    override fun setUserStopped(userStopped: Boolean) {
        _myNotificationListenerServiceState.update { it.copy(userStopped = userStopped) }
    }

    override fun setSelectedApps(selectedApps: List<AppInfo>) {
        _myNotificationListenerServiceState.update { it.copy(selectedApps = selectedApps) }
    }

    override fun setActiveSessionPackageName(activeSessionPackageName: String?) {
        _myNotificationListenerServiceState.update {
            it.copy(activeSessionPackageName = activeSessionPackageName)
        }
    }

    override fun setCurrentTrackTitle(new: String?) {
        _myNotificationListenerServiceState.update {
            it.copy(currentTrackTitle = new)
        }
    }

    override fun setCurrentTrackArtist(new: String?) {
        _myNotificationListenerServiceState.update {
            it.copy(currentTrackArtist = new)
        }
    }

    override fun setAudioRoute(route: AudioRouteSnapshot?) {
        _myNotificationListenerServiceState.update { it.copy(audioRoute = route) }
    }

    override fun setSystemVolume(volume: SystemVolumeSnapshot?) {
        _myNotificationListenerServiceState.update { it.copy(systemVolume = volume) }
    }

    override fun setRuntimeState(state: PlaybackRuntimeState) {
        _myNotificationListenerServiceState.update { it.copy(runtimeState = state) }
    }

    override fun setStarting(isStarting: Boolean) {
        _myNotificationListenerServiceState.update { it.copy(isStarting = isStarting) }
    }

    override fun setRestartResult(result: Boolean?) {
        _myNotificationListenerServiceState.update { it.copy(restartResult = result) }
    }

    override fun clearState() {
        _myNotificationListenerServiceState.update {
            MyNotificationListenerServiceState(userStopped = it.userStopped)
        }
    }
}
