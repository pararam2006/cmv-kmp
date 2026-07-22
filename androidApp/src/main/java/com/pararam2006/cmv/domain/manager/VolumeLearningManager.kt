package com.pararam2006.cmv.domain.manager

import com.pararam2006.cmv.ui.changeMode.AppMode
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface VolumeLearningManager {
    val volumeCommands: SharedFlow<Int>
    val debugState: StateFlow<VolumeState>

    fun onActiveSessionPackageNameChanged(newPackageName: String)
    fun onTrackChanged(
        title: String,
        artist: String?,
        offsetFromDb: Float,
        currentSystemVolume: Int,
        maxVolume: Int
    )

    fun onVolumeChanged(newVolume: Int)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onHeadsetStateChanged(isConnected: Boolean)
    fun onAudioFocusChanged(hasFocus: Boolean)
    fun onServiceStopped()
    fun onAppModeChanged(newMode: AppMode)
}