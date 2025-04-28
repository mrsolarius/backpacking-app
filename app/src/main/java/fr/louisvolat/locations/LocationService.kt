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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.louisvolat.R
import fr.louisvolat.data.repository.TrackingRepository
import fr.louisvolat.data.repository.TrackingRepositoryProvider
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.lifecycle.AppStateManager
import fr.louisvolat.lifecycle.AppStateManagerImpl
import fr.louisvolat.view.MainActivity
import fr.louisvolat.worker.UploadLocationsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocationService : LifecycleService(), LocationSaver {

    private lateinit var locationRequester: LocationRequester
    private lateinit var database: BackpakingLocalDataBase
    private lateinit var trackingRepository: TrackingRepository
    private var serviceScope = CoroutineScope(Dispatchers.Default)
    private var travelId: Long = -1
    private var isServiceStopping = false
    private var lastState = TrackingState()
    private var notificationRemoved = false

    private lateinit var appStateManager: AppStateManager

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
            // Si la notification est supprimée
            Action.NOTIFICATION_REMOVED.name -> {
                notificationRemoved = true
                Log.i("LocationService", "Notification supprimée")

                // Si on est en pause et que la notification est supprimée, on arrête le tracking
                if (lastState.isPaused) {
                    Log.i("LocationService", "En pause - arrêt du tracking")
                    stop()
                } else if (lastState.isTracking) {
                    // Si le tracking est actif, on recréé immédiatement la notification
                    Log.i("LocationService", "Tracking actif - recréation de la notification")
                    recreateNotification()
                }
            }
        }

        // Modification : Utiliser timerUpdates au lieu de trackingState
        // pour garantir la mise à jour régulière de la notification
        serviceScope.launch {
            trackingRepository.timerUpdates.collectLatest { state ->
                lastState = state // Sauvegarder le dernier état
                if (!isServiceStopping) {
                    // Si la notification a été supprimée et que le tracking est actif, la recréer
                    if (notificationRemoved && state.isTracking && !state.isPaused) {
                        recreateNotification()
                    } else if (!notificationRemoved) {
                        updateNotification(state)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun recreateNotification() {
        if (!isServiceStopping && lastState.isTracking) {
            notificationRemoved = false
            val notification = createNotification(lastState)
            startForeground(NOTIFICATION_ID, notification)
            Log.d("LocationService", "Notification recréée")
        }
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    private fun start() {
        Log.i("LocationService", "Service start called")
        isServiceStopping = false
        notificationRemoved = false

        // IMPORTANT: Démarrer d'abord le foreground service avec une notification initiale
        // pour éviter le ForegroundServiceDidNotStartInTimeException
        val initialState = TrackingState(isTracking = true, isPaused = false)
        val notification = createNotification(initialState)
        startForeground(NOTIFICATION_ID, notification)

        // Ensuite lancer l'action de démarrage du tracking dans le repository
        serviceScope.launch {
            trackingRepository.processAction(TrackingAction.Start(travelId))

            // Initialiser et démarrer la collecte de localisation
            locationRequester = LocationRequester(this@LocationService, 15000, 10f, this@LocationService)
            locationRequester.startLocationTracking()
            scheduleUploadLocationsWorker(travelId)

            broadcastTrackingStatus(true, false, travelId)
            Log.i("LocationService", "Tracking successfully started")
        }
    }

    private fun pause() {
        Log.i("LocationService", "Pause called")

        // Vérifier que le LocationRequester est initialisé avant de l'utiliser
        if (!::locationRequester.isInitialized) {
            Log.e("LocationService", "Cannot pause tracking, locationRequester not initialized")
            return
        }

        // Réinitialiser le flag de notification supprimée
        notificationRemoved = false

        serviceScope.launch {
            trackingRepository.processAction(TrackingAction.Pause)
            locationRequester.stopLocationTracking()

            broadcastTrackingStatus(true, true, travelId)
            Log.i("LocationService", "Tracking paused")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun resume() {
        Log.i("LocationService", "Resume called")

        // Réinitialiser le flag de notification supprimée
        notificationRemoved = false

        // Vérifier que le LocationRequester est initialisé avant de l'utiliser
        if (!::locationRequester.isInitialized) {
            Log.e("LocationService", "Cannot resume tracking, locationRequester not initialized")

            // Réinitialiser le locationRequester si nécessaire
            locationRequester = LocationRequester(this, 15000, 10f, this)
        }

        serviceScope.launch {
            // IMPORTANT: Nous voulons continuer le tracking en cours, pas en démarrer un nouveau
            trackingRepository.processAction(TrackingAction.Resume)
            locationRequester.startLocationTracking()
            broadcastTrackingStatus(true, false, travelId)
            Log.i("LocationService", "Tracking resumed")
        }
    }

    private fun stop() {
        Log.i("LocationService", "Stop called")

        // Marquer le service comme étant en cours d'arrêt pour éviter les mises à jour de notification
        isServiceStopping = true

        // Supprimer immédiatement la notification avant tout
        notificationManager.cancel(NOTIFICATION_ID)

        serviceScope.launch {
            try {
                // Récupérer le travelId actuel depuis le repository pour s'assurer qu'il est à jour
                val currentTravelId = lastState.currentTravelId ?: travelId

                // Arrêter le tracking dans le repository
                trackingRepository.processAction(TrackingAction.Stop)

                // Vérifier que le LocationRequester est initialisé avant de l'utiliser
                if (::locationRequester.isInitialized) {
                    locationRequester.stopLocationTracking()
                }

                // Gérer les workers
                WorkManager.getInstance(this@LocationService).cancelUniqueWork("UploadLocationsWorkerPeriodic")
                executeUploadLocationsWorkerOneTime(currentTravelId)

                // Arrêter explicitement le service en premier plan
                stopForeground(STOP_FOREGROUND_REMOVE)

                // Envoyer le broadcast
                broadcastTrackingStatus(false, false, currentTravelId)

                Log.i("LocationService", "Service successfully stopped")
            } catch (e: Exception) {
                Log.e("LocationService", "Error stopping service: ${e.message}")
            } finally {
                // S'assurer que le service est bien arrêté
                stopSelf()
            }
        }
    }

    private fun updateNotification(state: TrackingState) {
        if (!state.isTracking || isServiceStopping || notificationRemoved) return

        try {
            val notification = createNotification(state)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("LocationService", "Error updating notification: ${e.message}")
        }
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

        // Intent pour notification supprimée
        val notificationRemovedIntent = Intent(this, LocationService::class.java).apply {
            action = Action.NOTIFICATION_REMOVED.name
        }
        val pendingNotificationRemovedIntent = PendingIntent.getService(
            this, 3, notificationRemovedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Texte de la notification adapté à l'état actuel
        val statusText = if (state.isPaused) {
            getString(R.string.tracking_paused)
        } else {
            state.getFormattedActivityText()
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

        // Création de la notification avec le nouveau texte
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_channel))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(applicationContext, R.color.md_theme_primaryContainer_highContrast))
            .setColorized(true)
            .setContentIntent(pendingContentIntent)
            .setDeleteIntent(pendingNotificationRemovedIntent)
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
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun scheduleUploadLocationsWorker(travelId: Long) {
        Log.i("LocationService", "Scheduling UploadLocationsWorker for travelId: $travelId")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Créer les données d'entrée avec le travelId
        val inputData = createWorkerInputData(travelId)

        val uploadWorkerRequest = PeriodicWorkRequestBuilder<UploadLocationsWorker>(90, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UploadLocationsWorkerPeriodic",
            ExistingPeriodicWorkPolicy.KEEP,
            uploadWorkerRequest
        )
    }

    private fun executeUploadLocationsWorkerOneTime(travelId: Long) {
        Log.i("LocationService", "Executing one-time UploadLocationsWorker for travelId: $travelId")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Créer les données d'entrée avec le travelId
        val inputData = createWorkerInputData(travelId)

        val uploadWorkerRequest = OneTimeWorkRequest.Builder(UploadLocationsWorker::class.java)
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(uploadWorkerRequest)
    }

    private fun createWorkerInputData(travelId: Long): Data {
        return Data.Builder()
            .putLong("travel_id", travelId)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        database = BackpakingLocalDataBase.getDatabase(this)
        appStateManager = AppStateManagerImpl(applicationContext)
        appStateManager.startMonitoring()

        // Initialisation du repository
        trackingRepository = TrackingRepositoryProvider.getInstance(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Supprimer à nouveau la notification pour s'assurer qu'elle disparaît
        isServiceStopping = true
        notificationManager.cancel(NOTIFICATION_ID)

        try {
            serviceScope.cancel()

            if (::locationRequester.isInitialized) {
                locationRequester.stopLocationTracking()
            }

            // Essayer de forcer l'arrêt du service en premier plan
            stopForeground(STOP_FOREGROUND_REMOVE)

        } catch (e: Exception) {
            Log.e("LocationService", "Error in onDestroy: ${e.message}")
        }
    }

    override fun saveLocation(latitude: Double, longitude: Double, altitude: Double, time: Long) {
        if (travelId != -1L && !isServiceStopping) {
            serviceScope.launch {
                trackingRepository.processAction(
                    TrackingAction.UpdateLocation(
                        latitude = latitude,
                        longitude = longitude,
                        altitude = altitude,
                        time = time
                    )
                )
            }
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

        // Extras pour les broadcasts
        const val EXTRA_IS_TRACKING = "extra_is_tracking"
        const val EXTRA_IS_PAUSED = "extra_is_paused"
    }

    enum class Action {
        START,
        STOP,
        PAUSE,
        RESUME,
        NOTIFICATION_REMOVED
    }
}