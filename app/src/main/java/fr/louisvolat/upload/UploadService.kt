package fr.louisvolat.upload.service

import android.net.Uri
import fr.louisvolat.upload.state.UploadState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface définissant les opérations du service d'upload.
 * Respecte le principe d'inversion des dépendances (D de SOLID).
 */
interface UploadService {
    /**
     * Flux d'état exposant l'état actuel de l'upload
     */
    val uploadState: StateFlow<UploadState>

    /**
     * Démarre l'upload de plusieurs images pour un voyage spécifique
     * @param uris Liste des URIs des images à uploader
     * @param travelId ID du voyage associé aux images
     * @return Identifiant unique pour cet upload
     */
    suspend fun uploadImages(uris: List<Uri>, travelId: Long): String

    /**
     * Annule tous les uploads en cours
     */
    suspend fun cancelAllUploads()

    /**
     * Réessaie les uploads qui ont échoué
     */
    suspend fun retryFailedUploads()
}