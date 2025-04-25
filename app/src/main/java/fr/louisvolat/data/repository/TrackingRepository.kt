package fr.louisvolat.data.repository

import fr.louisvolat.database.dao.CoordinateDao
import fr.louisvolat.database.entity.Coordinate
import fr.louisvolat.lifecycle.AppStateManager
import fr.louisvolat.locations.TrackingAction
import fr.louisvolat.locations.TrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrackingRepository(
    private val coordinateDao: CoordinateDao,
    private val appStateManager: AppStateManager
): AppStateManager.AppStateListener  {
    // Changement: Utiliser un MutableSharedFlow pour l'état du tracking
    private val _trackingState = MutableSharedFlow<TrackingState>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val trackingState = _trackingState.asSharedFlow()

    // Garder une copie interne de l'état actuel
    private var currentState = TrackingState()

    // SharedFlow pour garantir que tous les événements sont reçus
    private val _trackingEvents = MutableSharedFlow<TrackingAction>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val trackingEvents = _trackingEvents.asSharedFlow()

    // SharedFlow spécifique pour les mises à jour de temps
    private val _timerUpdates = MutableSharedFlow<TrackingState>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val timerUpdates = _timerUpdates.asSharedFlow()

    // Dernière coordonnée capturée
    private val _lastCoordinate = MutableStateFlow<Coordinate?>(null)
    val lastCoordinate: StateFlow<Coordinate?> = _lastCoordinate.asStateFlow()

    // Coordonnées du trajet actuel
    private val _currentTravelCoordinates = MutableStateFlow<List<Coordinate>>(emptyList())
    val currentTravelCoordinates: StateFlow<List<Coordinate>> = _currentTravelCoordinates.asStateFlow()

    // Timer job pour les mises à jour périodiques
    private var timerJob: kotlinx.coroutines.Job? = null
    private val timerScope = CoroutineScope(Dispatchers.Default)
    private var timerUpdateIntervalMs: Long = 1000

    // Initialisation: émettre l'état initial
    init {
        appStateManager.registerAppStateListener(this)
        CoroutineScope(Dispatchers.Default).launch {
            _trackingState.emit(currentState)
        }
    }

    override fun onAppStateChanged(isInForeground: Boolean, isScreenOn: Boolean) {
        updateTimerFrequency(isInForeground, isScreenOn)
    }

    private fun updateTimerFrequency(isInForeground: Boolean, isScreenOn: Boolean) {
        timerUpdateIntervalMs = when {
            isInForeground -> 1000L // 1 seconde au premier plan
            !isScreenOn -> 15 * 60 * 1000L // 15 minutes quand écran éteint
            else -> 60 * 1000L // 1 minute en arrière-plan
        }

        // Redémarrer le timer avec la nouvelle fréquence si nécessaire
        if (currentState.isTracking && !currentState.isPaused) {
            stopTimerUpdates()
            startTimerUpdates()
        }
    }

    // Mise à jour de l'état de tracking
    suspend fun processAction(action: TrackingAction) {
        val newState = when (action) {
            is TrackingAction.Start -> {
                val startState = currentState.copy(
                    isTracking = true,
                    isPaused = false,
                    currentTravelId = action.travelId,
                    startTimeMillis = System.currentTimeMillis(),
                    totalPausedDurationMillis = 0,
                    pausedAtMillis = 0,
                    lastUpdateTime = System.currentTimeMillis()
                )

                // Démarrer le timer ici
                startTimerUpdates()

                startState
            }

            is TrackingAction.Stop -> {
                // Arrêter le timer ici
                stopTimerUpdates()

                currentState.copy(
                    isTracking = false,
                    isPaused = false,
                    lastUpdateTime = System.currentTimeMillis()
                )
            }

            is TrackingAction.Pause -> {
                currentState.copy(
                    isPaused = true,
                    pausedAtMillis = System.currentTimeMillis(),
                    lastUpdateTime = System.currentTimeMillis()
                )
            }

            is TrackingAction.Resume -> {
                // IMPORTANT: Calculer correctement la durée de pause
                // et préserver toutes les autres valeurs de l'état actuel
                val pauseDuration = if (currentState.pausedAtMillis > 0) {
                    System.currentTimeMillis() - currentState.pausedAtMillis
                } else {
                    0L
                }

                // On conserve startTimeMillis de l'état précédent
                currentState.copy(
                    isPaused = false,
                    totalPausedDurationMillis = currentState.totalPausedDurationMillis + pauseDuration,
                    lastUpdateTime = System.currentTimeMillis()
                )
            }

            is TrackingAction.UpdateLocation -> {
                // Mise à jour de l'état avec la nouvelle localisation
                val updatedState = currentState.copy(
                    lastLocationTime = action.time,
                    lastUpdateTime = System.currentTimeMillis()
                )

                // Sauvegarde en base de données
                currentState.currentTravelId?.let { travelId ->
                    saveCoordinate(
                        latitude = action.latitude,
                        longitude = action.longitude,
                        altitude = action.altitude,
                        time = action.time,
                        travelId = travelId
                    )
                }

                updatedState
            }

            is TrackingAction.UpdateState -> {
                // Préserver les valeurs importantes lors des mises à jour d'état
                val updatedState = action.state.copy(
                    // Conserver ces valeurs de l'état actuel si elles ne sont pas définies dans le nouvel état
                    startTimeMillis = if (action.state.startTimeMillis > 0) action.state.startTimeMillis else currentState.startTimeMillis,
                    totalPausedDurationMillis = if (action.state.totalPausedDurationMillis > 0) action.state.totalPausedDurationMillis else currentState.totalPausedDurationMillis,
                    lastUpdateTime = System.currentTimeMillis()
                )
                updatedState
            }

            // Nouvelle action pour les mises à jour du timer uniquement
            is TrackingAction.TimerTick -> {
                currentState.copy(lastUpdateTime = System.currentTimeMillis())
            }
        }

        // Mettre à jour l'état interne
        currentState = newState

        // Émettre le nouvel état
        _trackingState.emit(newState)
        _trackingEvents.emit(action)

        // Émettre également une mise à jour de timer si on est en tracking
        if (newState.isTracking) {
            _timerUpdates.emit(newState)
        }
    }

    // Démarrer les mises à jour périodiques du timer
    private fun startTimerUpdates() {
        stopTimerUpdates() // S'assurer qu'il n'y a pas déjà un timer en cours

        timerJob = timerScope.launch {
            while (true) {
                val state = currentState
                if (state.isTracking && !state.isPaused) {
                    // Émettre un TimerTick pour forcer une mise à jour
                    processAction(TrackingAction.TimerTick)
                }
                delay(timerUpdateIntervalMs)
            }
        }
    }

    // Arrêter les mises à jour périodiques
    private fun stopTimerUpdates() {
        timerJob?.cancel()
        timerJob = null
    }

    // Sauvegarder une coordonnée
    suspend fun saveCoordinate(latitude: Double, longitude: Double, altitude: Double, time: Long, travelId: Long) {
        val newCoordinate = Coordinate.createWithCurrentTimeZone(
            timestamp = time,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            travelId = travelId
        )

        coordinateDao.insert(newCoordinate)
        _lastCoordinate.value = newCoordinate

        // Mettre à jour la liste des coordonnées pour le trajet actuel
        if (currentState.currentTravelId == travelId) {
            loadCurrentTravelCoordinates(travelId)
        }
    }

    // Charger les coordonnées pour un trajet spécifique
    suspend fun loadCurrentTravelCoordinates(travelId: Long) {
        val coordinates = coordinateDao.getCoordinatesForTravel(travelId)
        _currentTravelCoordinates.value = coordinates
    }

    // Nettoyer les anciennes coordonnées
    suspend fun cleanOldCoordinates(olderThan: Long) {
        coordinateDao.deleteBeforeDate(olderThan)
    }

    // Récupérer toutes les coordonnées pour un trajet
    suspend fun getCoordinatesForTravel(travelId: Long): List<Coordinate> {
        return coordinateDao.getCoordinatesForTravel(travelId)
    }

    fun cleanup() {
        appStateManager.unregisterAppStateListener(this)
        stopTimerUpdates()
    }
}