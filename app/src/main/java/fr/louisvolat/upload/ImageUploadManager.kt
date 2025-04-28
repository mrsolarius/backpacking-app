package fr.louisvolat.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import fr.louisvolat.worker.UploadImageWorker
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire d'upload d'images suivant le principe de responsabilité unique (S de SOLID)
 * Cette classe coordonne les uploads d'images via WorkManager
 */
class ImageUploadManager(private val context: Context) {

    companion object {
        const val MAX_QUEUE_SIZE = 100
        const val UPLOAD_WORK_NAME = "image_upload_work"

        // Clés pour les données du worker
        const val KEY_UPLOAD_ID = "upload_id"
        const val KEY_URI_LIST = "uri_list"
        const val KEY_TRAVEL_ID = "travel_id"
        const val KEY_TOTAL_IMAGES = "total_images"
        const val KEY_CURRENT_INDEX = "current_index"

        @Volatile
        private var INSTANCE: ImageUploadManager? = null

        fun getInstance(context: Context): ImageUploadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ImageUploadManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val workManager = WorkManager.getInstance(context)

    // État actuel de l'upload
    private val _uploadState = MutableLiveData<UploadState>()
    val uploadState: LiveData<UploadState> = _uploadState

    // Initialisation de l'état
    init {
        _uploadState.value = UploadState()
    }

    /**
     * Démarre l'upload de plusieurs images avec les contraintes appropriées
     */
    fun uploadImages(uris: List<Uri>, travelId: Long): String {
        if (uris.isEmpty()) return ""

        // Limiter la taille de la file d'attente
        val limitedUris = if (uris.size > MAX_QUEUE_SIZE) uris.take(MAX_QUEUE_SIZE) else uris

        // Créer un ID unique pour cet ensemble d'uploads
        val uploadId = java.util.UUID.randomUUID().toString()

        // Trier les URI par taille de fichier (de la plus petite à la plus grande)
        val sortedUris = limitedUris.sortedBy { getFileSize(context, it) }

        // Préparer les données pour le worker
        val inputData = Data.Builder()
            .putString(KEY_UPLOAD_ID, uploadId)
            .putStringArray(KEY_URI_LIST, sortedUris.map { it.toString() }.toTypedArray())
            .putLong(KEY_TRAVEL_ID, travelId)
            .putInt(KEY_TOTAL_IMAGES, sortedUris.size)
            .putInt(KEY_CURRENT_INDEX, 0)
            .build()

        // Définir les contraintes pour l'exécution du travail
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Créer la requête de travail
        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadImageWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10000,
                TimeUnit.MILLISECONDS
            )
            .build()

        // Enregistrer l'état initial
        _uploadState.value = UploadState(
            isUploading = true,
            totalImages = sortedUris.size,
            uploadedImages = 0,
            currentImageIndex = 0,
            pendingUris = sortedUris
        )

        // Envoyer la requête au WorkManager
        workManager.enqueueUniqueWork(
            UPLOAD_WORK_NAME,
            ExistingWorkPolicy.APPEND,
            uploadWorkRequest
        )

        return uploadId
    }

    /**
     * Annule tous les uploads en cours
     */
    fun cancelAllUploads() {
        workManager.cancelUniqueWork(UPLOAD_WORK_NAME)
        _uploadState.value = UploadState(isUploading = false)
    }

    /**
     * Retourne l'état actuel de WorkInfo pour les uploads d'images
     */
    fun getWorkInfoLiveData(): LiveData<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkLiveData(UPLOAD_WORK_NAME)
    }

    /**
     * Reprend les uploads qui ont échoué
     */
    fun retryFailedUploads() {
        val currentState = _uploadState.value ?: return

        if (currentState.failedUris.isNotEmpty()) {
            uploadImages(currentState.failedUris, currentState.travelId)
        }
    }

    /**
     * Obtient la taille d'un fichier à partir de son URI
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Met à jour l'état d'upload avec les informations du worker
     */
    fun updateUploadState(uploadId: String, currentIndex: Int, success: Boolean, uri: Uri? = null) {
        val currentState = _uploadState.value ?: UploadState()

        if (currentState.uploadId != uploadId) return

        val newPendingUris = currentState.pendingUris.toMutableList()
        val newFailedUris = currentState.failedUris.toMutableList()

        if (uri != null) {
            newPendingUris.remove(uri)
            if (!success) {
                newFailedUris.add(uri)
            }
        }

        _uploadState.postValue(
            currentState.copy(
                uploadedImages = if (success) currentState.uploadedImages + 1 else currentState.uploadedImages,
                currentImageIndex = currentIndex,
                pendingUris = newPendingUris,
                failedUris = newFailedUris,
                isUploading = newPendingUris.isNotEmpty() || currentIndex < currentState.totalImages
            )
        )
    }
}