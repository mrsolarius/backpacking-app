package fr.louisvolat.api.mapper

import fr.louisvolat.api.dto.CreateCoordinateRequest
import fr.louisvolat.database.Converter
import fr.louisvolat.database.entity.Coordinate

class CoordinatePostMapper {
    companion object {
        fun mapToDTO(coordinate: Coordinate): CreateCoordinateRequest {

            return CreateCoordinateRequest(
                latitude = coordinate.latitude.toString(),
                longitude = coordinate.longitude.toString(),
                date = Converter.fromTimestampToZonedDateTime(coordinate)
            )
        }
    }
}