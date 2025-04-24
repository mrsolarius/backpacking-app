package fr.louisvolat.api.dto

data class CoordinateListPostDTO(
    val coordinates: List<CreateCoordinateRequest>
)

data class CoordinateDTO(
    val id: Long?,
    val latitude: String,
    val longitude: String,
    val date: String,
    val createdAt: String,
    val updatedAt: String
)

data class CreateCoordinateRequest(
    val latitude: String,
    val longitude: String,
    val date: String
)

data class CreateCoordinatesRequest(
    val coordinates: List<CreateCoordinateRequest>
)

data class CreateCoordinateResponseConfirm(
    val savedCoordinate: Long,
    val startDate: String,
    val endDate: String
)