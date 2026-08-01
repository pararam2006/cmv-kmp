package com.pararam2006.cmv.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackVolume(
    val id: Int = 0,
    val trackTitle: String,
    val artistName: String?,
    val volumeOffset: Float,
)
