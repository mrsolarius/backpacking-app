package fr.louisvolat.api.dto

import java.time.ZonedDateTime

data class TravelDTO(
    val id: Long?,
    val name: String,
    val description: String,
    val startDate: String,
    val endDate: String?,
    val coverPictureId: Long?,
    val userId: Long,
    val coordinates: List<CoordinateDTO>?,
    val pictures: List<PictureDTO>?,
    val createdAt: String,
    val updatedAt: String
)

data class CreateTravelRequest(
    val name: String,
    val description: String,
    val startDate: ZonedDateTime
)

data class UpdateTravelRequest(
    val name: String? = null,
    val description: String? = null,
    val startDate: ZonedDateTime? = null,
    val endDate: ZonedDateTime? = null,
    val coverPictureId: Long? = null
)
