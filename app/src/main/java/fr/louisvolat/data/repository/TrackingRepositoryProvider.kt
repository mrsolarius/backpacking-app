// Dans un nouveau fichier appelé TrackingRepositoryProvider.kt
package fr.louisvolat.data.repository

import android.content.Context
import fr.louisvolat.RunningApp
import fr.louisvolat.database.BackpakingLocalDataBase

object TrackingRepositoryProvider {
    private var instance: TrackingRepository? = null

    fun getInstance(context: Context): TrackingRepository {
        if (instance == null) {
            val database = BackpakingLocalDataBase.getDatabase(context.applicationContext)
            val coordinateDao = database.coordinateDao()

            // Obtenir l'AppStateManager via l'application
            val app = context.applicationContext as RunningApp
            val appStateManager = app.getAppStateManager()

            instance = TrackingRepository(coordinateDao, appStateManager)
        }
        return instance!!
    }

    fun cleanup() {
        instance?.cleanup()
        instance = null
    }
}