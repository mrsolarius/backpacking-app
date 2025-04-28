package fr.louisvolat.upload.state

import android.net.Uri

/**
 * Classe représentant l'état actuel de l'upload d'images
 * Respecte le principe de responsabilité unique (S de SOLID)
 */
data class UploadState(
    val isUploading: Boolean = false,
    val totalImages: Int = 0,
    val uploadedImages: Int = 0,
    val currentImageIndex: Int = 0,
    val pendingUris: List<Uri> = emptyList(),
    val failedUris: List<Uri> = emptyList(),
    val uploadId: String = "",
    val travelId: Long = -1L,
    val error: String? = null
) {
    /**
     * Calcule le pourcentage de progression de l'upload
     */
    val progress: Int
        get() = if (totalImages > 0) (uploadedImages * 100) / totalImages else 0

    /**
     * Texte de progression formaté
     */
    val progressText: String
        get() = "$uploadedImages / $totalImages"

    /**
     * Indique si l'upload est terminé
     */
    val isComplete: Boolean
        get() = totalImages > 0 && uploadedImages == totalImages

    /**
     * Indique s'il y a eu des erreurs pendant l'upload
     */
    val hasErrors: Boolean
        get() = failedUris.isNotEmpty() || error != null
}