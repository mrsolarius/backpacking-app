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
import fr.louisvolat.data.viewmodel.CoordinateViewModel
import fr.louisvolat.data.viewmodel.CoordinateViewModelFactory
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

        // Récupérer l'ID du voyage depuis les données d'entrée ou utiliser une valeur par défaut
        val travelId = inputData.getLong("travel_id", 1)

        // Initialiser le repository et le ViewModel
        val database = BackpakingLocalDataBase.getDatabase(applicationContext)
        val coordinateDao = database.coordinateDao()
        val apiClient = ApiClient.getInstance(applicationContext)
        val coordinateRepository = CoordinateRepository(coordinateDao, apiClient)
        val coordinateViewModel = CoordinateViewModelFactory(coordinateRepository).create(CoordinateViewModel::class.java)

        // Variables pour stocker le résultat
        var uploadSuccess = false
        var errorMessage: String? = null

        // Exécuter l'upload avec la méthode dédiée au Worker
        runBlocking {
            try {
                val result = coordinateViewModel.uploadCoordinatesForWorker(travelId, lastUpdate)
                result.fold(
                    onSuccess = { response ->
                        Log.i("UploadLocationsWorker", "Upload successful: ${response.savedCoordinate} coordinates saved")
                        uploadSuccess = true
                    },
                    onFailure = { exception ->
                        errorMessage = exception.message
                        Log.e("UploadLocationsWorker", "Upload failed: $errorMessage")
                    }
                )
            } catch (e: Exception) {
                errorMessage = e.message
                Log.e("UploadLocationsWorker", "Error during upload", e)
            }
        }

        // Traiter le résultat
        return if (uploadSuccess) {
            Log.i("UploadLocationsWorker", "Worker finished successfully")
            // Mettre à jour la date de dernière mise à jour
            CoroutineScope(Dispatchers.IO).launch {
                applicationContext.dataStore.edit { preferences ->
                    preferences[KEY_LAST_UPDATE] = System.currentTimeMillis()
                }
            }
            Result.success()
        } else {
            Log.e("UploadLocationsWorker", "Worker failed: $errorMessage")
            Result.retry()
        }
    }

}