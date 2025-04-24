package fr.louisvolat.data.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.louisvolat.data.repository.TrackingRepository
import fr.louisvolat.locations.TrackingAction
import fr.louisvolat.locations.TrackingState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackingViewModel(private val repository: TrackingRepository) : ViewModel() {

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Exposer l'état du tracking depuis le repository comme un StateFlow
    val trackingState = repository.trackingState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrackingState()
    )

    // Exposer les mises à jour du timer comme un StateFlow pour l'UI
    val timerUpdates = repository.timerUpdates.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrackingState()
    )

    /**
     * Démarre le tracking pour un voyage
     */
    fun startTracking(travelId: Long) {
        viewModelScope.launch {
            try {
                repository.processAction(TrackingAction.Start(travelId))
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur lors du démarrage du tracking"
            }
        }
    }

    /**
     * Met en pause le tracking
     */
    fun pauseTracking() {
        viewModelScope.launch {
            try {
                repository.processAction(TrackingAction.Pause)
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur lors de la mise en pause du tracking"
            }
        }
    }

    /**
     * Reprend le tracking après une pause
     */
    fun resumeTracking() {
        viewModelScope.launch {
            try {
                repository.processAction(TrackingAction.Resume)
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur lors de la reprise du tracking"
            }
        }
    }

    /**
     * Arrête le tracking
     */
    fun stopTracking() {
        viewModelScope.launch {
            try {
                repository.processAction(TrackingAction.Stop)
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur lors de l'arrêt du tracking"
            }
        }
    }

    /**
     * Vérifie si le tracking est actif
     */
    fun isTracking(): Boolean {
        return trackingState.value.isTracking
    }

    /**
     * Vérifie si le tracking est en pause
     */
    fun isPaused(): Boolean {
        return trackingState.value.isPaused
    }
}