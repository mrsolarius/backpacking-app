package fr.louisvolat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import fr.louisvolat.data.repository.TrackingRepositoryProvider
import fr.louisvolat.lifecycle.AppStateManager
import fr.louisvolat.lifecycle.AppStateManagerImpl
import fr.louisvolat.locations.LocationService

class RunningApp: Application() {
    private lateinit var appStateManager: AppStateManager

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            LocationService.CHANNEL_ID,
            getString(R.string.location_service_channel),
            NotificationManager.IMPORTANCE_HIGH
        )

        val notificationManager = getSystemService(NotificationManager::class.java) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Initialiser l'AppStateManager
        appStateManager = AppStateManagerImpl(applicationContext)
        appStateManager.startMonitoring()
    }

    override fun onTerminate() {
        super.onTerminate()
        // Nettoyer les ressources pour éviter les fuites de mémoire
        TrackingRepositoryProvider.cleanup()
    }

    fun getAppStateManager(): AppStateManager = appStateManager
}