package fr.louisvolat.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.louisvolat.api.ApiClient
import fr.louisvolat.upload.ImageUploadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID

/**
 * Worker amélioré pour l'upload d'images
 * Applique le principe de substitution de Liskov (L de SOLID) en étant interchangeable avec d'autres workers
 */
class UploadImageWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "UploadImageWorker"
    private val uploadManager = ImageUploadManager.getInstance(appContext)
    private val apiClient = ApiClient.getInstance(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val uploadId = inputData.getString(ImageUploadManager.KEY_UPLOAD_ID) ?: return@withContext Result.failure()
            val uriStrings = inputData.getStringArray(ImageUploadManager.KEY_URI_LIST) ?: return@withContext Result.failure()
            val travelId = inputData.getLong(ImageUploadManager.KEY_TRAVEL_ID, -1L)
            val totalImages = inputData.getInt(ImageUploadManager.KEY_TOTAL_IMAGES, 0)
            val startIndex = inputData.getInt(ImageUploadManager.KEY_CURRENT_INDEX, 0)

            if (travelId == -1L || uriStrings.isEmpty()) {
                Log.e(tag, "Invalid travel ID or empty URI list")
                return@withContext Result.failure()
            }

            var successCount = 0
            var currentIndex = startIndex

            // Pour chaque URI, tenter l'upload
            for (i in startIndex until uriStrings.size) {
                currentIndex = i
                val uriString = uriStrings[i]
                val uri = Uri.parse(uriString)

                // Mettre à jour la notification
                val currentProgress = (i * 100) / totalImages
                setProgressAsync(workDataOf(
                    "progress" to currentProgress,
                    "current_index" to i,
                    "total" to totalImages
                ))

                val result = uploadSingleImage(uri, travelId)

                // Mettre à jour l'état de l'upload
                uploadManager.updateUploadState(uploadId, i, result, uri)

                if (result) {
                    successCount++
                } else {
                    // Si l'upload échoue, continuer avec les autres images
                    Log.e(tag, "Failed to upload image: $uri")
                }

                // Vérifier si le travail a été annulé
                if (isStopped) {
                    Log.i(tag, "Upload work was cancelled")
                    break
                }
            }

            // Créer les données de sortie
            val outputData = workDataOf(
                "success_count" to successCount,
                "total_count" to uriStrings.size,
                "failed_count" to (uriStrings.size - successCount)
            )

            // Déterminer le résultat final
            return@withContext if (successCount == uriStrings.size) {
                Result.success(outputData)
            } else if (successCount > 0) {
                Result.success(outputData) // Considérer comme réussi même si certains ont échoué
            } else {
                Result.failure(outputData)
            }

        } catch (e: Exception) {
            Log.e(tag, "Error in upload worker", e)
            return@withContext Result.failure(
                workDataOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    /**
     * Upload une seule image
     */
    private suspend fun uploadSingleImage(uri: Uri, travelId: Long): Boolean {
        var tempFile: File? = null

        try {
            // Créer un fichier temporaire
            tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                Log.e(tag, "Failed to create temp file from URI: $uri")
                return false
            }

            // Préparer la requête multipart
            val requestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "picture",
                UUID.randomUUID().toString(),
                requestBody
            )

            // Exécuter l'upload
            val response = apiClient.pictureService.uploadPicture(travelId, filePart).execute()

            return response.isSuccessful

        } catch (e: Exception) {
            Log.e(tag, "Error uploading image", e)
            return false
        } finally {
            // Nettoyer le fichier temporaire
            tempFile?.delete()
        }
    }

    /**
     * Crée un fichier temporaire à partir d'un URI
     */
    private suspend fun createTempFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(applicationContext.cacheDir, "temp_upload_${System.currentTimeMillis()}.jpg")
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(tag, "Error creating temp file", e)
            null
        }
    }
}