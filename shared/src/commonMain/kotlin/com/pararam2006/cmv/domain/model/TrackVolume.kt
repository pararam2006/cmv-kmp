package com.pararam2006.cmv.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VolumeOffsetModel {
    LEGACY_RATIO,
    DECIBEL,
}

@Serializable
data class TrackVolume(
    val id: Int = 0,
    val trackTitle: String,
    val artistName: String?,
    /**
     * The serialized/database name stays `volumeOffset` so existing installations
     * can be migrated without dropping their rules.
     */
    @SerialName("volumeOffset")
    val volumeOffsetDb: Float,
    val offsetModel: VolumeOffsetModel = VolumeOffsetModel.LEGACY_RATIO,
)
