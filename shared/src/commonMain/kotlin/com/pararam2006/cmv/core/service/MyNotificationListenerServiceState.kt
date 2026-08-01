package com.pararam2006.cmv.core.service

import com.pararam2006.cmv.domain.model.AppInfo

data class MyNotificationListenerServiceState(
    val isConnected: Boolean = false,
    val userStopped: Boolean = false,
    val selectedApps: List<AppInfo> = emptyList(),
    val activeSessionPackageName: String? = null,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val isStarting: Boolean = false,
    val restartResult: Boolean? = null,
)
