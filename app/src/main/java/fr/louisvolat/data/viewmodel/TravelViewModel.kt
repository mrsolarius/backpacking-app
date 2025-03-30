package fr.louisvolat.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.database.entity.Travel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TravelViewModel(private val repository: TravelRepository) : ViewModel() {

    // Dans le ViewModel
    private val _travels = MutableStateFlow<List<Travel>>(emptyList())
    val travels: StateFlow<List<Travel>> = _travels

    init {
        viewModelScope.launch {
            repository.getAllTravels().collect {
                _travels.value = it
            }
        }
    }

    fun refreshTravels(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.refreshTravels(onComplete)
        }
    }
}