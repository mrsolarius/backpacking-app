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
import androidx.core.net.toUri
import fr.louisvolat.RunningApp

/**
 * Worker amélioré pour l'upload d'images
 * Résout les problèmes de synchronisation et d'initialisation
 */
class UploadImageWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "UploadImageWorker"

    private val uploadManager by lazy {
        try {
            Log.d(tag, "Initialisation de uploadManager")
            (appContext as RunningApp).imageUploadManager
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de l'initialisation de uploadManager", e)
            null
        }
    }

    private val apiClient by lazy {
        try {
            Log.d(tag, "Initialisation de apiClient")
            ApiClient.getInstance(appContext)
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de l'initialisation de apiClient", e)
            null
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "doWork méthode appelée")

            val uploadId = inputData.getString(ImageUploadManager.KEY_UPLOAD_ID) ?: return@withContext Result.failure()
            val uriStrings = inputData.getStringArray(ImageUploadManager.KEY_URI_LIST) ?: return@withContext Result.failure()
            val travelId = inputData.getLong(ImageUploadManager.KEY_TRAVEL_ID, -1L)
            val totalImages = inputData.getInt(ImageUploadManager.KEY_TOTAL_IMAGES, 0)
            val startIndex = inputData.getInt(ImageUploadManager.KEY_CURRENT_INDEX, 0)

            Log.d(tag, "Données d'entrée: uploadId=$uploadId, travelId=$travelId, totalImages=$totalImages, startIndex=$startIndex, uriCount=${uriStrings.size}")

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
                val uri = uriString.toUri()
                Log.d(tag, "Traitement de l'URI $i: $uriString")

                // Mettre à jour la notification
                val currentProgress = (i * 100) / totalImages
                setProgressAsync(workDataOf(
                    "progress" to currentProgress,
                    "current_index" to i,
                    "total" to totalImages
                ))

                val result = uploadSingleImage(uri, travelId)
                Log.d(tag, "Résultat upload image $i: $result")

                // IMPORTANT: S'assurer que updateUploadState est appelé et complété
                try {
                    Log.d(tag, "Mise à jour de l'état pour l'image $i")
                    uploadManager?.updateUploadState(uploadId, i, result, uri)
                    Log.d(tag, "État mis à jour avec succès pour l'image $i")
                } catch (e: Exception) {
                    Log.e(tag, "Erreur lors de la mise à jour de l'état pour l'image $i", e)
                }

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

            Log.d(tag, "Worker terminé: successCount=$successCount, totalCount=${uriStrings.size}")

            // Déterminer le résultat final
            return@withContext if (successCount == uriStrings.size) {
                Result.success(outputData)
            } else if (successCount > 0) {
                Result.success(outputData) // Considérer comme réussi même si certains ont échoué
            } else {
                Result.failure(outputData)
            }

        } catch (e: Exception) {
            Log.e(tag, "Erreur dans upload worker", e)
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
            Log.d(tag, "Début uploadSingleImage pour URI: $uri")

            // Créer un fichier temporaire
            tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                Log.e(tag, "Failed to create temp file from URI: $uri")
                return false
            }
            Log.d(tag, "Fichier temporaire créé: ${tempFile.absolutePath}, taille: ${tempFile.length()}")

            // Vérifier que l'API client est disponible
            if (apiClient == null) {
                Log.e(tag, "ApiClient est null, impossible de faire l'upload")
                return false
            }

            // Préparer la requête multipart
            val requestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "picture",
                UUID.randomUUID().toString(),
                requestBody
            )
            Log.d(tag, "MultipartBody préparé, envoi de la requête")

            // Exécuter l'upload
            val response = apiClient!!.pictureService.uploadPicture(travelId, filePart).execute()
            Log.d(tag, "Réponse reçue: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            return response.isSuccessful

        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de l'upload de l'image", e)
            return false
        } finally {
            // Nettoyer le fichier temporaire
            try {
                tempFile?.delete()
                Log.d(tag, "Fichier temporaire supprimé")
            } catch (e: Exception) {
                Log.e(tag, "Erreur lors de la suppression du fichier temporaire", e)
            }
        }
    }

    /**
     * Crée un fichier temporaire à partir d'un URI
     */
    private suspend fun createTempFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Création du fichier temporaire pour $uri")
            val tempFile = File(applicationContext.cacheDir, "temp_upload_${System.currentTimeMillis()}.jpg")

            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    Log.d(tag, "Copie terminée: $totalBytes octets écrits")
                }
            } ?: run {
                Log.e(tag, "Impossible d'ouvrir l'InputStream pour l'URI: $uri")
                return@withContext null
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(tag, "Fichier temporaire invalide: existe=${tempFile.exists()}, taille=${tempFile.length()}")
                return@withContext null
            }

            Log.d(tag, "Fichier temporaire créé avec succès: ${tempFile.length()} octets")
            return@withContext tempFile
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de la création du fichier temporaire", e)
            return@withContext null
        }
    }
}