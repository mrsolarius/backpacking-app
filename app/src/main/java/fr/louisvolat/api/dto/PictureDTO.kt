package fr.louisvolat.api.dto

data class PictureDTO(
    val id: Long?,
    val path: String,
    val latitude: String,
    val longitude: String,
    val altitude: String?,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val versions: Map<String, List<PictureVersionsDTO>>
)

data class PictureVersionsDTO(
    val id: Long?,
    val pictureId: Long,
    val path: String,
    val resolution: Byte,
    val versionType: String,
    val createdAt: String,
    val updatedAt: String
)