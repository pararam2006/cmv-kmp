package com.pararam2006.cmv.data.repository

import com.pararam2006.cmv.data.local.TrackVolumeDao
import com.pararam2006.cmv.data.mapper.toDomain
import com.pararam2006.cmv.data.mapper.toEntity
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.utils.StringNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackVolumeRepositoryImpl(
    private val dao: TrackVolumeDao,
) : TrackVolumeRepository {
    override fun getAllTrackVolumes(): Flow<List<TrackVolume>> {
        return dao.getAllTrackVolumes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTrackVolume(title: String, artist: String?): TrackVolume? {
        val normTitle = StringNormalizer.normalize(title)
        val normArtist = artist?.let { StringNormalizer.normalize(it) }

        return dao.getTrackVolume(normTitle, normArtist)?.toDomain()
    }

    override suspend fun getTrackVolumeById(id: Int): TrackVolume? {
        return dao.getTrackVolumeById(id)?.toDomain()
    }

    override suspend fun saveTrackVolume(trackVolume: TrackVolume) {
        dao.insertTrackVolume(trackVolume.toEntity())
    }

    override suspend fun deleteTrackVolume(trackVolume: TrackVolume) {
        dao.deleteTrackVolume(trackVolume.toEntity())
    }

    override suspend fun deleteTrackVolumeById(id: Int) = dao.deleteTrackVolumeById(id)
}
