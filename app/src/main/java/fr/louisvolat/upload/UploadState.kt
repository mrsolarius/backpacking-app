package fr.louisvolat.upload

import android.net.Uri

/**
 * Classe représentant l'état actuel de l'upload d'images
 */
data class UploadState(
    val isUploading: Boolean = false,
    val totalImages: Int = 0,
    val uploadedImages: Int = 0,
    val currentImageIndex: Int = 0,
    val pendingUris: List<Uri> = emptyList(),
    val failedUris: List<Uri> = emptyList(),
    val uploadId: String = "",
    val travelId: Long = -1L
) {
    val progress: Int
        get() = if (totalImages > 0) (uploadedImages * 100) / totalImages else 0

    val progressText: String
        get() = "$uploadedImages / $totalImages"

    val isComplete: Boolean
        get() = totalImages > 0 && uploadedImages == totalImages
}