package fr.louisvolat.data.repository

import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.mapper.TravelMapper
import fr.louisvolat.database.dao.TravelDao
import fr.louisvolat.database.entity.Travel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

class TravelRepository(
    private val travelDao: TravelDao,
    private val apiClient: ApiClient,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val ioScope = CoroutineScope(ioDispatcher)

    fun getAllTravels(): Flow<List<Travel>> = travelDao.getAll()

    fun refreshTravels(onComplete: () -> Unit) {
        CoroutineScope(ioDispatcher).launch {
            try {
                val response = apiClient.travelService.getMyTravels().execute()
                response.body()?.let { dtos ->
                    // Conversion et insertion
                    val travels = TravelMapper.toEntityList(dtos)
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