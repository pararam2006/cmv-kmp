package com.pararam2006.cmv.data.mapper

import com.pararam2006.cmv.data.local.TrackVolumeEntity
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.utils.StringNormalizer

fun TrackVolumeEntity.toDomain(): TrackVolume {
    return TrackVolume(
        id = id,
        trackTitle = trackTitle,
        artistName = artistName,
        volumeOffset = volumeOffset,
    )
}

fun TrackVolume.toEntity(): TrackVolumeEntity {
    return TrackVolumeEntity(
        id = id,
        trackTitle = StringNormalizer.normalize(trackTitle),
        artistName = artistName?.let { StringNormalizer.normalize(it) },
        volumeOffset = volumeOffset,
    )
}
