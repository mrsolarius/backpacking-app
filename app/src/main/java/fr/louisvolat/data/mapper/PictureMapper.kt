package fr.louisvolat.data.mapper

import fr.louisvolat.api.dto.PictureDTO
import fr.louisvolat.database.entity.Picture

object PictureMapper : AbstractMapper<PictureDTO, Picture>() {
    override fun toEntity(dto: PictureDTO): Picture {
        return Picture(
            id = dto.id ?: 0,
            latitude = dto.latitude,
            longitude = dto.longitude,
            altitude = dto.altitude,
            date = parseDateToTimestamp(dto.createdAt),
            rawVersion = dto.path,
            localPath = null,
            travelId = null,
            createdAt = parseDateToTimestamp(dto.createdAt),
            updatedAt = parseDateToTimestamp(dto.updatedAt)
        )
    }

    fun toEntityWithLocalPathAndTravelId(dto: PictureDTO, localPath: String, travelId: Long): Picture {
        return Picture(
            id = dto.id ?: 0,
            latitude = dto.latitude,
            longitude = dto.longitude,
            altitude = dto.altitude,
            date = parseDateToTimestamp(dto.createdAt),
            rawVersion = dto.path,
            localPath = localPath,
            travelId = travelId,
            createdAt = parseDateToTimestamp(dto.createdAt),
            updatedAt = parseDateToTimestamp(dto.updatedAt)
        )
    }

}