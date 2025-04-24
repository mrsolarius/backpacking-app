package fr.louisvolat.locations

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.louisvolat.R
import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.repository.CoordinateRepository
import fr.louisvolat.data.viewmodel.CoordinateViewModel
import fr.louisvolat.data.viewmodel.CoordinateViewModelFactory
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.view.MainActivity
import fr.louisvolat.worker.UploadLocationsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocationService : LifecycleService(), LocationSaver {

    private lateinit var locationRequester: LocationRequester
    private lateinit var database: BackpakingLocalDataBase
    private lateinit var coordinateViewModel: CoordinateViewModel
    private var serviceScope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null
    private var travelId: Long = -1
    private var startTime: Long = 0
    private var pausedTime: Long = 0
    private var totalPausedTime: Long = 0
    private var isTracking = false
    private var isPaused = false

    private val binder = LocalBinder()
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            Action.START.name -> {
                travelId = intent.getLongExtra(EXTRA_TRAVEL_ID, -1)
                if (travelId != -1L) {
                    start()
                } else {
                    Log.e("LocationService", "No travel ID provided, cannot start tracking")
                    stopSelf()
                }
            }
            Action.PAUSE.name -> {
                pause()
            }
            Action.RESUME.name -> {
                resume()
            }
            Action.STOP.name -> {
                stop()
            }
        }

        lifecycleScope.launch {
            SharedTrackingManager.trackingState.collect { state ->
                updateNotification(state)
            }
        }

        return START_STICKY
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    private fun start() {
        if (isTracking) return

        startTime = System.currentTimeMillis()
        isTracking = true
        isPaused = false
        totalPausedTime = 0

        // Notifier le SharedTrackingManager
        SharedTrackingManager.processAction(TrackingAction.Start(travelId))

        val state = TrackingState(
            isTracking = true,
            isPaused = false,
            currentTravelId = travelId,
            startTimeMillis = startTime
        )

        val notification = createNotification(state)
        startForeground(NOTIFICATION_ID, notification)

        locationRequester = LocationRequester(this, 15000, 10f, this)
        locationRequester.startLocationTracking()
        scheduleUploadLocationsWorker()

        // Démarrer le timer pour la mise à jour de la notification
        startTimer()

        broadcastTrackingStatus(true, false, travelId)
        Log.i("LocationService", "Service successfully started")
    }

    private fun pause() {
        if (!isTracking || isPaused) return

        isPaused = true
        pausedTime = System.currentTimeMillis()

        // Arrêter la collecte de localisation
        locationRequester.stopLocationTracking()

        // Notifier le SharedTrackingManager
        SharedTrackingManager.processAction(TrackingAction.Pause)

        broadcastTrackingStatus(true, true, travelId)
        Log.i("LocationService", "Tracking paused")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun resume() {
        if (!isTracking || !isPaused) return

        // Calculer le temps pausé et l'ajouter au total
        val pauseDuration = System.currentTimeMillis() - pausedTime
        totalPausedTime += pauseDuration

        isPaused = false

        // Redémarrer la collecte de localisation
        locationRequester.startLocationTracking()

        // Notifier le SharedTrackingManager
        SharedTrackingManager.processAction(TrackingAction.Resume)

        broadcastTrackingStatus(true, false, travelId)
        Log.i("LocationService", "Tracking resumed")
    }

    private fun stop() {
        if (!isTracking) return

        isTracking = false
        isPaused = false

        locationRequester.stopLocationTracking()
        WorkManager.getInstance(this).cancelUniqueWork("UploadLocationsWorkerPeriodic")
        executeUploadLocationsWorkerOneTime()

        // Annuler le timer
        timerJob?.cancel()

        // Notifier le SharedTrackingManager
        SharedTrackingManager.processAction(TrackingAction.Stop)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        broadcastTrackingStatus(false, false, travelId)
        Log.i("LocationService", "Service successfully stopped")
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isTracking) {
                if (!isPaused) {
                    val currentTime = System.currentTimeMillis()
                    val trackingDuration = currentTime - startTime - totalPausedTime

                    // Mettre à jour l'état dans le SharedTrackingManager toutes les secondes
                    val state = TrackingState(
                        isTracking = isTracking,
                        isPaused = isPaused,
                        currentTravelId = travelId,
                        startTimeMillis = startTime,
                        totalPausedDurationMillis = totalPausedTime
                    )

                    SharedTrackingManager.processAction(
                        TrackingAction.UpdateState(state)
                    )
                    updateNotification(state)

                    // Broadcast pour mise à jour supplémentaire au fragment si nécessaire
                    val intent = Intent(ACTION_TRACKING_TIME_UPDATE).apply {
                        putExtra(EXTRA_TRACKING_DURATION, trackingDuration)
                    }

                    sendBroadcast(intent)
                }
                delay(1000) // Mise à jour chaque seconde
            }
        }
    }

    private fun updateNotification(state: TrackingState) {
        if (!isTracking) return

        val notification = createNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(state: TrackingState): android.app.Notification {
        // Intent pour ouvrir l'application au fragment de tracking
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingContentIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent pour pause/resume
        val pauseResumeIntent = Intent(this, LocationService::class.java).apply {
            action = if (state.isPaused) Action.RESUME.name else Action.PAUSE.name
        }
        val pendingPauseResumeIntent = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent pour arrêter
        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = Action.STOP.name
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Texte de la notification adapté à l'état actuel
        val statusText = if (state.isPaused) {
            getString(R.string.tracking_paused)
        } else {
            getString(R.string.tracking_running)
        }

        // Bouton pause/reprise adapté à l'état actuel
        val pauseResumeActionText = if (state.isPaused) {
            getString(R.string.resume_tracking_notif)
        } else {
            getString(R.string.pause_tracking_notif)
        }

        val pauseResumeIcon = if (state.isPaused) {
            R.drawable.outlined_play_arrow_24
        } else {
            R.drawable.outlined_pause_24
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_channel))
            .setContentText("$statusText - ${state.getFormattedDuration()}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(applicationContext, R.color.md_theme_surfaceContainerLow))
            .setColorized(true)
            .setContentIntent(pendingContentIntent)
            .addAction(
                pauseResumeIcon,
                pauseResumeActionText,
                pendingPauseResumeIntent
            )
            .addAction(
                R.drawable.outlined_stop_24,
                getString(R.string.stop_tracking_notif),
                pendingStopIntent
            )
            .setOnlyAlertOnce(true)  // Pour éviter que la notification flashe à chaque mise à jour
            .setOngoing(true)
            .build()
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
        database = BackpakingLocalDataBase.getDatabase(this)

        // Initialisation du repository et du ViewModel
        val coordinateDao = database.coordinateDao()
        val apiClient = ApiClient.getInstance(this)
        val coordinateRepository = CoordinateRepository(coordinateDao, apiClient)
        coordinateViewModel = CoordinateViewModelFactory(coordinateRepository).create(CoordinateViewModel::class.java)

    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        if (isTracking) {
            stop()
        }
    }

    override fun saveLocation(latitude: Double, longitude: Double, altitude: Double, time: Long) {
        if (travelId != -1L) {
            coordinateViewModel.saveCoordinate(latitude, longitude, altitude, time, travelId)

            // Notifier le SharedTrackingManager de la nouvelle localisation
            SharedTrackingManager.processAction(
                TrackingAction.UpdateLocation(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    time = time
                )
            )
        }
    }

    private fun broadcastTrackingStatus(isTracking: Boolean, isPaused: Boolean, travelId: Long) {
        val intent = Intent(ACTION_TRACKING_STATUS_CHANGED).apply {
            putExtra(EXTRA_IS_TRACKING, isTracking)
            putExtra(EXTRA_IS_PAUSED, isPaused)
            putExtra(EXTRA_TRAVEL_ID, travelId)
        }
        sendBroadcast(intent)
    }

    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val EXTRA_TRAVEL_ID = "extra_travel_id"
        const val NOTIFICATION_ID = 1

        // Actions pour les broadcasts
        const val ACTION_TRACKING_STATUS_CHANGED = "fr.louisvolat.TRACKING_STATUS_CHANGED"
        const val ACTION_TRACKING_TIME_UPDATE = "fr.louisvolat.TRACKING_TIME_UPDATE"

        // Extras pour les broadcasts
        const val EXTRA_IS_TRACKING = "extra_is_tracking"
        const val EXTRA_IS_PAUSED = "extra_is_paused"
        const val EXTRA_TRACKING_DURATION = "extra_tracking_duration"
    }

    enum class Action {
        START,
        STOP,
        PAUSE,
        RESUME
    }
}