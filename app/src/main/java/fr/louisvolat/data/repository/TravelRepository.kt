package fr.louisvolat.data.repository

import android.net.Uri
import android.content.Context
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

class TravelRepository(
    private val travelDao: TravelDao,
    private val pictureDao: PictureDao,
    private val apiClient: ApiClient,
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

    // Nouvelle méthode pour créer un voyage
    suspend fun createTravel(
        name: String,
        description: String,
        startDate: ZonedDateTime,
        selectedImageUri: Uri? = null,
        context: Context? = null
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

                    // Todo: Associer l'image au voyage

                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}