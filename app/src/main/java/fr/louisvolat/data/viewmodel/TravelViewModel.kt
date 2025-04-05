package fr.louisvolat.data.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.louisvolat.api.dto.TravelDTO
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.database.entity.TravelWithCoverPicture
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class TravelViewModel(private val repository: TravelRepository) : ViewModel() {

    // État pour gérer le chargement et les erreurs
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _createTravelSuccess = MutableLiveData<TravelDTO?>()
    val createTravelSuccess: LiveData<TravelDTO?> = _createTravelSuccess

    // Récupérer directement les voyages avec leurs images de couverture
    val travelsWithCoverPictures: StateFlow<List<TravelWithCoverPicture>> = repository.getAllTravelsWithCoverPictures()

    fun refreshTravels(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.refreshTravels(onComplete)
        }
    }

    // Nouvelle méthode pour créer un voyage
    fun createTravel(
        name: String,
        description: String,
        startDate: ZonedDateTime,
        selectedImageUri: Uri? = null,
        context: Context? = null
    ) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val result = repository.createTravel(
                    name = name,
                    description = description,
                    startDate = startDate,
                    selectedImageUri = selectedImageUri,
                    context = context
                )

                result.fold(
                    onSuccess = { travel ->
                        _createTravelSuccess.value = travel
                    },
                    onFailure = { exception ->
                        _error.value = exception.message ?: "Une erreur s'est produite"
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Une erreur s'est produite"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Réinitialiser l'état après création
    fun resetCreationState() {
        _createTravelSuccess.value = null
        _error.value = null
    }
}