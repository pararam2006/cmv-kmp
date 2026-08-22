package com.pararam2006.cmv.data.mapper

import com.pararam2006.cmv.data.local.TrackVolumeEntity
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.utils.StringNormalizer

fun TrackVolumeEntity.toDomain(): TrackVolume {
    return TrackVolume(
        id = id,
        trackTitle = trackTitle,
        artistName = artistName,
        volumeOffsetDb = volumeOffset,
        offsetModel = runCatching { VolumeOffsetModel.valueOf(offsetModel) }.getOrDefault(VolumeOffsetModel.LEGACY_RATIO),
    )
}

fun TrackVolume.toEntity(): TrackVolumeEntity {
    return TrackVolumeEntity(
        id = id,
        trackTitle = StringNormalizer.normalize(trackTitle),
        artistName = artistName?.let { StringNormalizer.normalize(it) },
        volumeOffset = volumeOffsetDb,
        offsetModel = offsetModel.name,
    )
}
