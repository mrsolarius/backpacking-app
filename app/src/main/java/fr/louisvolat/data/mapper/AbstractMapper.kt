package fr.louisvolat.data.mapper

import java.time.ZonedDateTime

abstract class AbstractMapper<DTO,Entity> {

    fun parseDateToTimestamp(dateString: String): Long {
        return ZonedDateTime.parse(dateString).toInstant().toEpochMilli()
    }

    abstract fun toEntity(dto: DTO): Entity

    fun toEntityList(dtos: List<DTO>): List<Entity> {
        return dtos.map { toEntity(it) }
    }
}