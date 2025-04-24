package fr.louisvolat.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.louisvolat.data.repository.CoordinateRepository

@Suppress("UNCHECKED_CAST")
class CoordinateViewModelFactory(private val repository: CoordinateRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CoordinateViewModel::class.java)) {
            return CoordinateViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}