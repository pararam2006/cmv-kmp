package com.pararam2006.cmv.core.service

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.utils.logDebug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MyNotificationListenerServiceStateHolder {
    private val _myNotificationListenerServiceState =
        MutableStateFlow(MyNotificationListenerServiceState())
    val state = _myNotificationListenerServiceState.asStateFlow()

    init {
        logDebug("init, instanceId=${hashCode()}")
    }

    fun setConnected(isConnected: Boolean) {
        logDebug("isConnected=$isConnected")
        _myNotificationListenerServiceState.update { it.copy(isConnected = isConnected) }
    }

    fun setUserStopped(userStopped: Boolean) {
        logDebug("userStopped=$userStopped")
        _myNotificationListenerServiceState.update { it.copy(userStopped = userStopped) }
    }

    fun setSelectedApps(selectedApps: List<AppInfo>) {
        logDebug("setSelectedApps count=${selectedApps.size}")
        _myNotificationListenerServiceState.update { it.copy(selectedApps = selectedApps) }
    }

    fun setActiveSessionPackageName(activeSessionPackageName: String?) {
        logDebug("package=$activeSessionPackageName")
        _myNotificationListenerServiceState.update {
            it.copy(activeSessionPackageName = activeSessionPackageName)
        }
    }

    fun setCurrentTrackTitle(new: String?) {
        _myNotificationListenerServiceState.update {
            it.copy(currentTrackTitle = new)
        }
    }

    fun setCurrentTrackArtist(new: String?) {
        _myNotificationListenerServiceState.update {
            it.copy(currentTrackArtist = new)
        }
    }

    fun clearState() {
        logDebug("reset state to defaults")
        _myNotificationListenerServiceState.update {
            MyNotificationListenerServiceState()
        }
    }
}
