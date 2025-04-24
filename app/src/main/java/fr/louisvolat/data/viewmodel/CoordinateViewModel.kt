package fr.louisvolat.data.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.louisvolat.api.dto.CoordinateDTO
import fr.louisvolat.api.dto.CreateCoordinateResponseConfirm
import fr.louisvolat.data.repository.CoordinateRepository
import fr.louisvolat.database.entity.Coordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class CoordinateViewModel(private val repository: CoordinateRepository) : ViewModel() {

    // État pour gérer le chargement et les erreurs
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Pour maintenir la dernière localisation enregistrée
    private val _lastCoordinate = MutableStateFlow<Coordinate?>(null)
    val lastCoordinate: StateFlow<Coordinate?> = _lastCoordinate.asStateFlow()

    // Pour les coordonnées associées à un voyage spécifique
    private val _travelCoordinates = MutableStateFlow<List<CoordinateDTO>>(emptyList())
    val travelCoordinates: StateFlow<List<CoordinateDTO>> = _travelCoordinates.asStateFlow()

    /**
     * Sauvegarde une nouvelle coordonnée dans la base de données locale
     */
    fun saveCoordinate(latitude: Double, longitude: Double, altitude: Double, time: Long, travelId: Long) {
        viewModelScope.launch {
            try {
                repository.saveCoordinate(latitude, longitude, altitude, time, travelId)
                // Mise à jour de la dernière coordonnée enregistrée
                val newCoordinate = Coordinate.createWithCurrentTimeZone(
                    timestamp = time,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    travelId = travelId
                )
                _lastCoordinate.value = newCoordinate
            } catch (e: Exception) {
                _error.value = e.message ?: "Une erreur s'est produite lors de l'enregistrement de la coordonnée"
            }
        }
    }

    /**
     * Télécharge les coordonnées sur le serveur
     */
    suspend fun uploadCoordinatesForWorker(travelId: Long, lastUploadTime: Long): Result<CreateCoordinateResponseConfirm> {
        return repository.uploadCoordinates(travelId, lastUploadTime)
    }

    /**
     * Obtient toutes les coordonnées stockées localement
     */
    suspend fun getAllCoordinates(): List<Coordinate> {
        return repository.getAllCoordinates()
    }

    /**
     * Obtient les coordonnées depuis une date spécifique
     */
    suspend fun getCoordinatesFromDate(date: Long): List<Coordinate> {
        return repository.getCoordinatesFromDate(date)
    }

    /**
     * Nettoie les anciennes coordonnées
     */
    fun cleanOldCoordinates(olderThan: Long = Instant.now().minusSeconds(60 * 60 * 24 * 7).toEpochMilli()) {
        viewModelScope.launch {
            repository.cleanCoordinatesBeforeDate(olderThan)
        }
    }

    /**
     * Charge les coordonnées pour un voyage spécifique depuis l'API
     */
    fun loadCoordinatesForTravel(travelId: Long) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.getCoordinatesFlowForTravel(travelId).collect { coordinates ->
                    _travelCoordinates.value = coordinates
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur lors du chargement des coordonnées"
            } finally {
                _isLoading.value = false
            }
        }
    }
}