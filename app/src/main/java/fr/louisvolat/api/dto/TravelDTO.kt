package fr.louisvolat.api.dto

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class TravelDTO(
    val id: Long?,
    val name: String,
    val description: String,
    val startDate: String,
    val endDate: String?,
    val coverPicture: PictureDTO?,
    val userId: Long,
    val coordinates: List<CoordinateDTO>?,
    val pictures: List<PictureDTO>?,
    val createdAt: String,
    val updatedAt: String
)

data class CreateTravelRequest(
    val name: String,
    val description: String,
    val startDate: String
) {
    companion object {
        fun convertToISO8601(instant: Instant): String {
            return DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC) // Important: spécifiez le fuseau horaire UTC
                .format(instant)
        }

        fun buildTravelRequest(
            description: String,
            name: String,
            startDate: ZonedDateTime
        ): CreateTravelRequest {
            val isoDate = convertToISO8601(startDate.toInstant())
            return CreateTravelRequest(description, name, isoDate)
        }
    }
}

data class UpdateTravelRequest(
    val name: String? = null,
    val description: String? = null,
    val startDate: ZonedDateTime? = null,
    val endDate: ZonedDateTime? = null,
    val coverPictureId: Long? = null
)
