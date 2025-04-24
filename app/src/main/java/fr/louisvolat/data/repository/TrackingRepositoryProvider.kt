// Dans un nouveau fichier appelé TrackingRepositoryProvider.kt
package fr.louisvolat.data.repository

import android.content.Context
import fr.louisvolat.database.BackpakingLocalDataBase

object TrackingRepositoryProvider {
    private var instance: TrackingRepository? = null

    fun getInstance(context: Context): TrackingRepository {
        if (instance == null) {
            val database = BackpakingLocalDataBase.getDatabase(context.applicationContext)
            val coordinateDao = database.coordinateDao()
            instance = TrackingRepository(coordinateDao)
        }
        return instance!!
    }
}