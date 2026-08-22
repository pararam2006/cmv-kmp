package com.pararam2006.cmv.core.service

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.platform.AudioRouteSnapshot
import com.pararam2006.cmv.platform.PlaybackRuntimeState
import com.pararam2006.cmv.platform.SystemVolumeSnapshot

data class MyNotificationListenerServiceState(
    val isConnected: Boolean = false,
    val userStopped: Boolean = false,
    val selectedApps: List<AppInfo> = emptyList(),
    val activeSessionPackageName: String? = null,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val audioRoute: AudioRouteSnapshot? = null,
    val systemVolume: SystemVolumeSnapshot? = null,
    val runtimeState: PlaybackRuntimeState = PlaybackRuntimeState(),
    val isStarting: Boolean = false,
    val restartResult: Boolean? = null,
)
