package fr.louisvolat.locations

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.louisvolat.R
import fr.louisvolat.database.Coordinate
import fr.louisvolat.database.CoordinateDatabase
import fr.louisvolat.worker.UploadLocationsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocationService : Service(), LocationSaver {

    private lateinit var locationRequester: LocationRequester
    private lateinit var database: CoordinateDatabase

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Action.START.name -> {
                start()
            }

            Action.STOP.name -> {
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    private fun start() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_channel))
            .setContentText(getString(R.string.tracking_running))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.stop_tracking_notif),
                Intent(this, LocationService::class.java).apply {
                    action = Action.STOP.name
                }.let { stopIntent ->
                    PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            )
            .setDeleteIntent(
                Intent(this, LocationService::class.java).apply {
                    action = Action.STOP.name
                }.let { stopIntent ->
                    PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            )
            .build()
        startForeground(1, notification)

        locationRequester = LocationRequester(this, 10000, 0f,this)
        locationRequester.startLocationTracking()
        scheduleUploadLocationsWorker()
        Log.i("LocationService", "Service successfully started")
    }

    private fun scheduleUploadLocationsWorker() {
        Log.i("LocationService", "Scheduling UploadLocationsWorker")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkerRequest = PeriodicWorkRequestBuilder<UploadLocationsWorker>(90, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UploadLocationsWorkerPeriodic",
            ExistingPeriodicWorkPolicy.KEEP,
            uploadWorkerRequest
        )
    }

    private fun stop() {
        locationRequester.stopLocationTracking()
        WorkManager.getInstance(this).cancelUniqueWork("UploadLocationsWorkerPeriodic")
        executeUploadLocationsWorkerOneTime()
        stopSelf()
        Log.i("LocationService", "Service successfully stopped")
    }

    private fun executeUploadLocationsWorkerOneTime() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkerRequest = OneTimeWorkRequest.Builder(UploadLocationsWorker::class.java)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(uploadWorkerRequest)
    }

    override fun onCreate() {
        super.onCreate()
        database = CoordinateDatabase.getDatabase(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        const val CHANNEL_ID = "location_service_channel"
    }

    enum class Action {
        START,
        STOP
    }

    override fun saveLocation(latitude: Double, longitude: Double, altitude: Double, time: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val coordinate = Coordinate(time,latitude, longitude, altitude)
            database.coordinateDao().insert(coordinate)
        }
    }


}