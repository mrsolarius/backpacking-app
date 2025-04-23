package fr.louisvolat.locations

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SharedTrackingManager {
    // État du tracking exposé comme un StateFlow immutable
    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    // SharedFlow qui garantit que toutes les émissions sont reçues
    private val _trackingUpdates = MutableSharedFlow<TrackingState>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val trackingUpdates = _trackingUpdates.asSharedFlow()

    // Méthode pour mettre à jour l'état avec des actions
    fun processAction(action: TrackingAction) {
        val currentState = _trackingState.value

        val newState = when (action) {
            is TrackingAction.Start -> {
                currentState.copy(
                    isTracking = true,
                    isPaused = false,
                    currentTravelId = action.travelId,
                    startTimeMillis = System.currentTimeMillis(),
                    totalPausedDurationMillis = 0,
                    pausedAtMillis = 0
                )
            }

            is TrackingAction.Stop -> {
                currentState.copy(
                    isTracking = false,
                    isPaused = false
                )
            }

            is TrackingAction.Pause -> {
                currentState.copy(
                    isPaused = true,
                    pausedAtMillis = System.currentTimeMillis()
                )
            }

            is TrackingAction.Resume -> {
                // Calculer le temps de pause
                val pauseDuration = System.currentTimeMillis() - currentState.pausedAtMillis
                currentState.copy(
                    isPaused = false,
                    totalPausedDurationMillis = currentState.totalPausedDurationMillis + pauseDuration
                )
            }

            is TrackingAction.UpdateLocation -> {
                currentState.copy(
                    lastLocationTime = action.time
                )
            }

            is TrackingAction.UpdateState -> {
                currentState.copy(
                    isTracking = action.state.isTracking,
                    isPaused = action.state.isPaused,
                    currentTravelId = action.state.currentTravelId,
                    startTimeMillis = action.state.startTimeMillis,
                    pausedAtMillis = action.state.pausedAtMillis,
                    totalPausedDurationMillis = action.state.totalPausedDurationMillis,
                    lastLocationTime = action.state.lastLocationTime,
                )
            }
        }

        _trackingState.value = newState
        CoroutineScope(Dispatchers.Default).launch {
            _trackingUpdates.emit(newState)
        }
    }
}