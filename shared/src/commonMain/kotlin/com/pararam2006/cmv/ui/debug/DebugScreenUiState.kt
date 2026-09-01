package com.pararam2006.cmv.ui.debug

import com.pararam2006.cmv.core.service.MyNotificationListenerServiceState
import com.pararam2006.cmv.domain.manager.VolumeState
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.domain.service.PlayingTrack

data class DebugScreenUiState(
    val serviceState: MyNotificationListenerServiceState = MyNotificationListenerServiceState(),
    val volumeState: VolumeState = VolumeState(),
    val coordinatorTrack: PlayingTrack? = null,
    val detectorSeesHeadphones: Boolean? = null,
    val serviceSupported: Boolean = true,
    val notificationPermissionGranted: Boolean = false,
    val appMode: AppMode = AppMode.LEARNING,
    val learningTimeSeconds: Int = 15,
    val showSystemVolumeUi: Boolean = true,
    val volumeJumpProtectionEnabled: Boolean = false,
    val observedAtMs: Long = 0,
)
