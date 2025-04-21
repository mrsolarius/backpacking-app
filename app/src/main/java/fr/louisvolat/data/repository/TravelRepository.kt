package fr.louisvolat.data.repository

import android.net.Uri
import android.content.Context
import androidx.lifecycle.LiveData
import fr.louisvolat.api.ApiClient
import fr.louisvolat.api.dto.CreateTravelRequest
import fr.louisvolat.data.mapper.PictureMapper
import fr.louisvolat.data.mapper.TravelMapper
import fr.louisvolat.database.dao.PictureDao
import fr.louisvolat.database.dao.TravelDao
import fr.louisvolat.database.entity.TravelWithCoverPicture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZonedDateTime
import fr.louisvolat.api.dto.TravelDTO
import fr.louisvolat.database.entity.Travel

class TravelRepository(
    private val travelDao: TravelDao,
    private val pictureDao: PictureDao,
    private val apiClient: ApiClient,
    private val pictureRepository: PictureRepository,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val ioScope = CoroutineScope(ioDispatcher)

    // Cache des résultats pour les StateFlow
    private val _travelsWithCoverPictures = MutableStateFlow<List<TravelWithCoverPicture>>(emptyList())

    init {
        // Initialiser les flux de données
        ioScope.launch {
            travelDao.getAllWithCoverPictures().collect {
                _travelsWithCoverPictures.value = it
            }
        }
    }

    fun getAllTravelsWithCoverPictures(): StateFlow<List<TravelWithCoverPicture>> = _travelsWithCoverPictures

    fun refreshTravels(onComplete: () -> Unit) {
        CoroutineScope(ioDispatcher).launch {
            try {
                val response = apiClient.travelService.getMyTravels().execute()
                response.body()?.let { dtos ->
                    // Conversion et insertion
                    val travels = TravelMapper.toEntityList(dtos)
                    val coverPictures = dtos.mapNotNull { it.coverPicture }.let { PictureMapper.toEntityList(it) }

                    pictureDao.insertAll(coverPictures)
                    travelDao.insertAll(travels)
                }
            } finally {
                withContext(mainDispatcher) {
                    onComplete()
                }
            }
        }
    }

    // Méthode pour créer un voyage (sans image de couverture)
    suspend fun createTravel(
        name: String,
        description: String,
        startDate: ZonedDateTime
    ): Result<TravelDTO> = withContext(ioDispatcher) {
        try {
            val createRequest = CreateTravelRequest.buildTravelRequest(
                name = name,
                description = description,
                startDate = startDate
            )

            val response = apiClient.travelService.createTravel(createRequest).execute()

            if (response.isSuccessful) {
                val createdTravel = response.body()

                createdTravel?.let {
                    // Insérer le voyage dans la BDD locale
                    val travelEntity = TravelMapper.toEntity(it)
                    travelDao.insert(travelEntity)
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Méthode pour créer un voyage avec image de couverture
    suspend fun createTravelWithCoverPicture(
        name: String,
        description: String,
        startDate: ZonedDateTime,
        selectedImageUri: Uri,
        context: Context
    ): Result<TravelDTO> = withContext(ioDispatcher) {
        try {
            // Étape 1: Créer le voyage
            val travelResult = createTravel(name, description, startDate)

            if (travelResult.isFailure) {
                return@withContext travelResult
            }

            val travel = travelResult.getOrNull()!!

            // Étape 2: Ajouter l'image
            val pictureResult = pictureRepository.uploadPicture(context, selectedImageUri, travel.id!!)

            if (pictureResult.isFailure) {
                return@withContext Result.failure(pictureResult.exceptionOrNull() ?: Exception("Failed to upload picture"))
            }

            val picture = pictureResult.getOrNull()!!

            // Étape 3: Définir l'image comme couverture
            val coverResult = pictureRepository.setCoverPicture(travel.id, picture.id!!)

            if (coverResult.isFailure) {
                return@withContext Result.failure(coverResult.exceptionOrNull() ?: Exception("Failed to set cover picture"))
            }

            // Étape 4: Mettre à jour l'entité locale
            val travelEntity = travelDao.getById(travel.id)
            travelEntity?.let {
                val updatedTravelEntity = it.copy(coverPictureId = picture.id)
                travelDao.update(updatedTravelEntity)
            }

            Result.success(travel.copy(coverPicture = picture))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Méthode pour définir une image existante comme couverture
    suspend fun setTravelCoverPicture(travelId: Long, pictureId: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            val result = pictureRepository.setCoverPicture(travelId, pictureId)

            if (result.isSuccess) {
                // Mettre à jour l'entité locale
                val travelEntity = travelDao.getById(travelId)
                travelEntity?.let {
                    val updatedTravelEntity = it.copy(coverPictureId = pictureId)
                    travelDao.update(updatedTravelEntity)
                }
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTravelById(travelId: Long): LiveData<Travel> {
        return travelDao.getByIdLive(travelId)
    }
}