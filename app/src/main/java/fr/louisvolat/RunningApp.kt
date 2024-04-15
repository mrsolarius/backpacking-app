package fr.louisvolat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import fr.louisvolat.services.LocationService

class RunningApp: Application() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            LocationService.CHANNEL_ID,
            getString(R.string.location_service_channel),
            NotificationManager.IMPORTANCE_HIGH
        )

        val notificationManager = getSystemService(NotificationManager::class.java) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

}