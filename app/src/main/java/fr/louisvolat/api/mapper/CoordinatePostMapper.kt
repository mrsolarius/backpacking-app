package fr.louisvolat.api.mapper

import fr.louisvolat.api.dto.CoordinatePostBodyDTO
import fr.louisvolat.database.Converter
import fr.louisvolat.database.Coordinate

class CoordinatePostMapper {
    companion object {
        fun mapToDTO(coordinate: Coordinate): CoordinatePostBodyDTO {

            return CoordinatePostBodyDTO(
                latitude = coordinate.latitude.toString(),
                longitude = coordinate.longitude.toString(),
                date = Converter.fromTimestampToString(coordinate.date)!!
            )
        }
    }
}