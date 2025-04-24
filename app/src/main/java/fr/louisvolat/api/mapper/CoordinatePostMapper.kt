package fr.louisvolat.api.mapper

import fr.louisvolat.api.dto.CreateCoordinateRequest
import fr.louisvolat.database.entity.Coordinate

class CoordinatePostMapper {
    companion object {
        fun mapToDTO(coordinate: Coordinate): CreateCoordinateRequest {
            val zonedDateTime = coordinate.toZonedDateTime() // Utilise la méthode existante de Coordinate
            val formattedDate = zonedDateTime.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            return CreateCoordinateRequest(
                latitude = coordinate.latitude.toString(),
                longitude = coordinate.longitude.toString(),
                date = formattedDate
            )
        }
    }
}