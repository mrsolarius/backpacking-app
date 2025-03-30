package fr.louisvolat.data.repository

import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.mapper.PictureMapper
import fr.louisvolat.data.mapper.TravelMapper
import fr.louisvolat.database.dao.PictureDao
import fr.louisvolat.database.dao.TravelDao
import fr.louisvolat.database.entity.TravelWithCoverPicture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

                    pictureDao.insertAll(coverPictures) // Doit être suspendu
                    travelDao.insertAll(travels) // Doit être suspendu
                }
            } finally {
                withContext(mainDispatcher) {
                    onComplete()
                }
            }
        }
    }
}