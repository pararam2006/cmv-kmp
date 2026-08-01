package com.pararam2006.cmv.domain.manager

import com.pararam2006.cmv.domain.model.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class VolumeCommand(
    val targetVolume: Int,
    val trackTitle: String,
    val trackArtist: String?,
    val trackGeneration: Long,
)

interface VolumeLearningManager {
    val volumeCommands: Flow<VolumeCommand>
    val debugState: StateFlow<VolumeState>

    fun onActiveSessionPackageNameChanged(newPackageName: String?)
    fun onTrackChanged(
        title: String,
        artist: String?,
        offsetFromDb: Float,
        currentSystemVolume: Int,
        maxVolume: Int,
        trackGeneration: Long,
    )

    fun onVolumeChanged(newVolume: Int)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onHeadsetStateChanged(isConnected: Boolean)
    fun onAudioFocusChanged(hasFocus: Boolean)
    fun onSessionDetached()
    fun onServiceStopped()
    fun onAppModeChanged(newMode: AppMode)
}