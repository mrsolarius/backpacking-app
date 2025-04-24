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
import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.repository.CoordinateRepository
import fr.louisvolat.database.BackpakingLocalDataBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val KEY_LAST_UPDATE = longPreferencesKey("last_update")

class UploadLocationsWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Log.i("UploadLocationsWorker", "Worker started")

        // Récupérer la dernière date de mise à jour
        val lastUpdateFlow: Flow<Long> = applicationContext.dataStore.data.map { preferences ->
            preferences[KEY_LAST_UPDATE] ?: 0
        }
        val lastUpdate = runBlocking { lastUpdateFlow.first() }

        // Récupérer l'ID du voyage depuis les données d'entrée
        val travelId = inputData.getLong(KEY_TRAVEL_ID, -1)

        // Vérifier que le travelId est valide
        if (travelId == -1L) {
            Log.e("UploadLocationsWorker", "Invalid travel_id: $travelId")
            return Result.failure()
        }

        Log.i("UploadLocationsWorker", "Processing coordinates for travel_id: $travelId")

        // Initialiser les repositories
        val database = BackpakingLocalDataBase.getDatabase(applicationContext)
        val coordinateDao = database.coordinateDao()
        val apiClient = ApiClient.getInstance(applicationContext)

        // Utiliser le repository de coordonnées pour l'upload
        val coordinateRepository = CoordinateRepository(coordinateDao, apiClient)

        // Variables pour stocker le résultat
        var uploadSuccess = false
        var errorMessage: String? = null

        // Exécuter l'upload avec le repository
        runBlocking {
            try {
                val result = coordinateRepository.uploadCoordinates(travelId, lastUpdate)
                result.fold(
                    onSuccess = { response ->
                        Log.i("UploadLocationsWorker", "Upload successful: ${response.savedCoordinate} coordinates saved for travel_id: $travelId")
                        uploadSuccess = true
                    },
                    onFailure = { exception ->
                        errorMessage = exception.message
                        Log.e("UploadLocationsWorker", "Upload failed for travel_id: $travelId. Error: $errorMessage")
                    }
                )
            } catch (e: Exception) {
                errorMessage = e.message
                Log.e("UploadLocationsWorker", "Error during upload for travel_id: $travelId", e)
            }
        }

        // Traiter le résultat
        return if (uploadSuccess) {
            Log.i("UploadLocationsWorker", "Worker finished successfully for travel_id: $travelId")
            // Mettre à jour la date de dernière mise à jour
            CoroutineScope(Dispatchers.IO).launch {
                applicationContext.dataStore.edit { preferences ->
                    preferences[KEY_LAST_UPDATE] = System.currentTimeMillis()
                }
            }
            Result.success()
        } else {
            Log.e("UploadLocationsWorker", "Worker failed for travel_id: $travelId: $errorMessage")
            Result.retry()
        }
    }

    companion object {
        // Clé pour les données d'entrée
        const val KEY_TRAVEL_ID = "travel_id"
    }
}