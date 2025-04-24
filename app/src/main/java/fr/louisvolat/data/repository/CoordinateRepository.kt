package fr.louisvolat.data.repository

import fr.louisvolat.api.ApiClient
import fr.louisvolat.api.dto.CoordinateDTO
import fr.louisvolat.api.dto.CreateCoordinateResponseConfirm
import fr.louisvolat.api.dto.CreateCoordinatesRequest
import fr.louisvolat.api.mapper.CoordinatePostMapper
import fr.louisvolat.database.dao.CoordinateDao
import fr.louisvolat.database.entity.Coordinate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant

class CoordinateRepository(
    private val coordinateDao: CoordinateDao,
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // Récupérer toutes les coordonnées en local
    suspend fun getAllCoordinates(): List<Coordinate> = withContext(ioDispatcher) {
        coordinateDao.getAll()
    }

    // Récupérer les coordonnées depuis une date
    suspend fun getCoordinatesFromDate(date: Long): List<Coordinate> = withContext(ioDispatcher) {
        coordinateDao.getFromDate(date)
    }

    // Ajouter une coordonnée en local
    suspend fun saveCoordinate(latitude: Double, longitude: Double, altitude: Double, time: Long, travelId: Long) =
        withContext(ioDispatcher) {
            val coordinate = Coordinate.createWithCurrentTimeZone(
                timestamp = time,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                travelId = travelId
            )
            coordinateDao.insert(coordinate)
        }

    // Envoyer les coordonnées au serveur
    suspend fun uploadCoordinates(travelId: Long, lastUploadTime: Long): Result<CreateCoordinateResponseConfirm> =
        withContext(ioDispatcher) {
            try{
                // Récupérer les coordonnées non encore envoyées
                val coordinates = coordinateDao.getFromDate(lastUploadTime)

                if (coordinates.isEmpty()) {
                    return@withContext Result.success(
                        CreateCoordinateResponseConfirm(
                            savedCoordinate = 0,
                            startDate = Instant.now().atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            endDate = Instant.now().atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        )
                    )
                }

                // Mapper les coordonnées pour l'API
                val coordinateRequests = coordinates.map(CoordinatePostMapper::mapToDTO)

                // Créer la requête
                val request = CreateCoordinatesRequest(coordinateRequests)

                // Exécuter l'appel API
                val response = apiClient.coordinateService.addCoordinatesToTravel(travelId, request).execute()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(Exception("Response body is null"))
                    }
                } else {
                    Result.failure(Exception("API error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // Nettoyer les anciennes coordonnées
    suspend fun cleanCoordinatesBeforeDate(date: Long) = withContext(ioDispatcher) {
        coordinateDao.deleteBeforeDate(date)
    }

    // Flux de coordonnées pour un voyage spécifique
    fun getCoordinatesFlowForTravel(travelId: Long): Flow<List<CoordinateDTO>> = flow {
        try {
            val response = apiClient.coordinateService.getCoordinatesByTravel(travelId).execute()
            if (response.isSuccessful) {
                response.body()?.let { emit(it) }
            }
        } catch (e: Exception) {
            // Gérer l'erreur silencieusement ou ré-émettre selon les besoins
        }
    }
}