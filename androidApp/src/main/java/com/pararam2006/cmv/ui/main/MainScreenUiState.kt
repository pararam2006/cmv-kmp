package com.pararam2006.cmv.ui.main

import com.pararam2006.cmv.domain.model.TrackVolume

data class MainScreenUiState(
    val tracks: List<TrackVolume> = emptyList(),
    val dialogTrack: TrackVolume? = null,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showHeadsetNotConnectedDialog: Boolean = false,
    val isHeadset: Boolean = false,
    val isTitleVisible: Boolean = true,
    val searchQuery: String = "",
    val isTracksLoading: Boolean = true,
    val currentPlayingTrack: String? = null,
    val currentPlayingArtist: String? = null,
    val offsetToNewTrack: Float = 1f,
)