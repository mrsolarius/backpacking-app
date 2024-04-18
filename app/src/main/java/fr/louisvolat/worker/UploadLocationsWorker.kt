package fr.louisvolat.worker

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Worker
import androidx.work.WorkerParameters
import fr.louisvolat.api.dto.CoordinateListPostDTO
import fr.louisvolat.api.mapper.CoordinatePostMapper
import fr.louisvolat.api.services.CoordinateService
import fr.louisvolat.database.CoordinateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UploadLocationsWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    var retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.1.10:8000")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val KEY_LAST_UPDATE = longPreferencesKey("last_update")
    override fun doWork(): Result {
        //log the worker
        Log.i("UploadLocationsWorker", "Worker started")
        val lastUpdateFlow: Flow<Long> = applicationContext.dataStore.data.map { preferences ->
            preferences[KEY_LAST_UPDATE] ?: 0
        }

        val lastUpdate = runBlocking { lastUpdateFlow.first() }
        val coordinatesList = CoordinateDatabase.getDatabase(applicationContext).coordinateDao()
            .getFromDate(lastUpdate).map(CoordinatePostMapper::mapToDTO)
        if (coordinatesList.isEmpty()) {
            Log.i("UploadLocationsWorker", "No data to send")
            return Result.success()
        }
        val postCoordinate = retrofit.create(CoordinateService::class.java).postCoordinates(
            CoordinateListPostDTO(coordinatesList)
        )
        val requestResult = postCoordinate.execute()

        return if (requestResult.isSuccessful) {
            Log.i("UploadLocationsWorker", "Worker finished")
            CoroutineScope(Dispatchers.IO).launch {
                applicationContext.dataStore.edit { preferences ->
                    preferences[KEY_LAST_UPDATE] = System.currentTimeMillis()
                }
            }
            Result.success()
        } else {
            Log.e("UploadLocationsWorker", "Worker failed with code ${requestResult.code()}")
            Result.retry()
        }
    }

}