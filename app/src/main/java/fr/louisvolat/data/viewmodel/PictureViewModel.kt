package fr.louisvolat.data.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.louisvolat.api.dto.PictureDTO
import fr.louisvolat.data.repository.PictureRepository
import fr.louisvolat.database.entity.Picture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PictureViewModel(private val repository: PictureRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _pictures = MutableStateFlow<List<PictureDTO>>(emptyList())
    val pictures: StateFlow<List<PictureDTO>> = _pictures.asStateFlow()

    private val _localPictures = MutableStateFlow<List<Picture>>(emptyList())
    val localPictures: StateFlow<List<Picture>> = _localPictures.asStateFlow()

    private val _operationSuccess = MutableLiveData<OperationStatus>()
    val operationSuccess: LiveData<OperationStatus> = _operationSuccess

    /**
     * Enumération des opérations possibles
     */
    enum class OperationStatus {
        NONE,
        SET_COVER_SUCCESS,
        DELETE_SUCCESS
    }

    /**
     * Charge les photos d'un voyage depuis l'API et la base de données locale
     */
    fun loadPicturesForTravel(travelId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // D'abord, essayer de charger depuis la base de données locale
                val localPics = repository.getLocalPicturesForTravel(travelId)
                _localPictures.value = localPics

                // Ensuite, rafraîchir depuis l'API
                repository.getPicturesForTravel(travelId).fold(
                    onSuccess = { picturesList ->
                        _pictures.value = picturesList
                        // Rafraîchir les données locales aussi
                        val updatedLocalPics = repository.getLocalPicturesForTravel(travelId)
                        _localPictures.value = updatedLocalPics
                    },
                    onFailure = { exception ->
                        _error.value = exception.message ?: "Une erreur s'est produite"
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Une erreur inattendue s'est produite"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * S'abonner aux mises à jour des photos en temps réel
     */
    fun observePicturesForTravelFromAPI(travelId: Long) {
        viewModelScope.launch {
            repository.getPicturesForTravelAsFlow(travelId).collectLatest { picturesList ->
                _pictures.value = picturesList
                // Rafraîchir les données locales
                val updatedLocalPics = repository.getLocalPicturesForTravel(travelId)
                _localPictures.value = updatedLocalPics
            }
        }
    }

    /**
     * Définit une photo comme image de couverture
     */
    fun setCoverPicture(travelId: Long, pictureId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                repository.setCoverPicture(travelId, pictureId).fold(
                    onSuccess = {
                        _operationSuccess.value = OperationStatus.SET_COVER_SUCCESS
                    },
                    onFailure = { exception ->
                        _error.value = exception.message ?: "Erreur lors de la définition de l'image de couverture"
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Une erreur inattendue s'est produite"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Supprime une photo
     */
    fun deletePicture(travelId: Long, pictureId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                repository.deletePicture(travelId, pictureId).fold(
                    onSuccess = {
                        _operationSuccess.value = OperationStatus.DELETE_SUCCESS
                        // Rafraîchir la liste des photos
                        loadPicturesForTravel(travelId)
                    },
                    onFailure = { exception ->
                        _error.value = exception.message ?: "Erreur lors de la suppression de l'image"
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Une erreur inattendue s'est produite"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Réinitialise l'état de succès des opérations
     */
    fun resetOperationState() {
        _operationSuccess.value = OperationStatus.NONE
        _error.value = null
    }
}