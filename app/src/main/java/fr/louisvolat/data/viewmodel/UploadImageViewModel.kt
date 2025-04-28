package fr.louisvolat.upload.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.louisvolat.upload.service.UploadService
import fr.louisvolat.upload.state.UploadState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UploadImageViewModel(private val uploadService: UploadService) : ViewModel() {

    /**
     * Flux d'état exposant l'état actuel de l'upload
     */
    val uploadState: StateFlow<UploadState> = uploadService.uploadState

    /**
     * Démarre l'upload de plusieurs images
     * @param uris Liste des URIs des images à uploader
     * @param travelId ID du voyage associé aux images
     */
    fun uploadImages(uris: List<Uri>, travelId: Long) {
        if (uris.isEmpty() || travelId == -1L) return

        viewModelScope.launch {
            uploadService.uploadImages(uris, travelId)
        }
    }

    /**
     * Annule tous les uploads en cours
     */
    fun cancelAllUploads() {
        viewModelScope.launch {
            uploadService.cancelAllUploads()
        }
    }

    /**
     * Réessaie les uploads qui ont échoué
     */
    fun retryFailedUploads() {
        viewModelScope.launch {
            uploadService.retryFailedUploads()
        }
    }
}