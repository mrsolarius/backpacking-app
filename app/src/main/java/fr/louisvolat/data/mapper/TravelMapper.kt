package fr.louisvolat.data.mapper

import fr.louisvolat.api.dto.TravelDTO
import fr.louisvolat.database.entity.Travel
import java.time.ZonedDateTime

object TravelMapper {
    fun toEntity(dto: TravelDTO): Travel {
        return Travel(
            id = dto.id ?: 0,
            name = dto.name,
            description = dto.description,
            startDate = parseDateToTimestamp(dto.startDate),
            endDate = dto.endDate?.let { parseDateToTimestamp(it) },
            coverPictureId = dto.coverPicture?.id
        )
    }

    fun toEntityList(dtos: List<TravelDTO>) = dtos.map { toEntity(it) }

    private fun parseDateToTimestamp(dateString: String): Long {
        return ZonedDateTime.parse(dateString).toInstant().toEpochMilli()
    }
}