package fr.louisvolat.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.louisvolat.data.repository.PictureRepository

class PictureViewModelFactory(private val repository: PictureRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PictureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PictureViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}