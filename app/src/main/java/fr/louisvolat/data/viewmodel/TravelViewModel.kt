package fr.louisvolat.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.database.entity.TravelWithCoverPicture
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TravelViewModel(private val repository: TravelRepository) : ViewModel() {

    // Récupérer directement les voyages avec leurs images de couverture
    val travelsWithCoverPictures: StateFlow<List<TravelWithCoverPicture>> = repository.getAllTravelsWithCoverPictures()

    fun refreshTravels(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.refreshTravels(onComplete)
        }
    }
}