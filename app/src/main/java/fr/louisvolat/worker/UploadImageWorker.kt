package fr.louisvolat.worker

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.louisvolat.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import androidx.core.net.toUri
import fr.louisvolat.upload.service.UploadImageService

/**
 * Worker amélioré pour l'upload d'images sans dépendance à ImageUploadManager.
 * Communique l'état via setProgressAsync.
 */
class UploadImageWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "UploadImageWorker"

    // Clés pour les données de progression spécifiques à chaque image
    companion object {
        const val KEY_PROGRESS_UPLOAD_ID = "progress_upload_id"
        const val KEY_PROGRESS_INDEX = "progress_index"
        const val KEY_PROGRESS_URI = "progress_uri"
        const val KEY_PROGRESS_SUCCESS = "progress_success"
        const val KEY_PROGRESS_IS_LAST =
            "progress_is_last" // Indique si c'est la dernière image traitée

        // Clés pour les données de sortie finales (peut correspondre aux clés de UploadImageService si souhaité)
        const val KEY_OUTPUT_SUCCESS_COUNT = "output_success_count"
        const val KEY_OUTPUT_FAILED_COUNT = "output_failed_count"
        const val KEY_OUTPUT_TOTAL_COUNT = "output_total_count"
        const val KEY_OUTPUT_ERROR = "output_error"
    }

    // Suppression de la dépendance à ImageUploadManager
    // private val uploadManager by lazy { ... }

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
        // Le bloc try commence ici, englobant toute la logique principale
        try {
            val uploadId = inputData.getString(UploadImageService.KEY_UPLOAD_ID) ?: run {
                Log.e(tag, "ID d'upload manquant dans les données d'entrée.")
                return@withContext Result.failure(workDataOf(KEY_OUTPUT_ERROR to "ID d'upload manquant"))
            }
            val uriStrings = inputData.getStringArray(UploadImageService.KEY_URI_LIST) ?: run {
                Log.e(tag, "Liste d'URIs manquante dans les données d'entrée.")
                return@withContext Result.failure(workDataOf(KEY_OUTPUT_ERROR to "Liste d'URIs manquante"))
            }
            val travelId = inputData.getLong(UploadImageService.KEY_TRAVEL_ID, -1L)
            val totalImages = uriStrings.size // Utiliser la taille réelle du tableau

            Log.d(
                tag,
                "doWork appelé: uploadId=$uploadId, travelId=$travelId, totalImages=$totalImages, uriCount=${uriStrings.size}"
            )

            if (travelId == -1L || uriStrings.isEmpty()) {
                Log.e(tag, "ID de voyage invalide ou liste d'URI vide.")
                return@withContext Result.failure(workDataOf(KEY_OUTPUT_ERROR to "ID de voyage invalide ou liste d'URI vide."))
            }

            var successCount = 0

            // Pour chaque URI, tenter l'upload et rapporter le statut
            for (index in uriStrings.indices) {
                val uriString = uriStrings[index]
                val uri = uriString.toUri()
                Log.d(tag, "Traitement de l'URI $index: $uriString")

                // Vérifier si le travail a été annulé avant de commencer l'upload
                if (isStopped) {
                    Log.i(tag, "Travail annulé avant de traiter l'image $index")
                    break // Sortir de la boucle
                }

                val isSuccess = uploadSingleImage(uri, travelId)
                Log.d(tag, "Résultat upload image $index: ${if (isSuccess) "Succès" else "Échec"}")

                if (isSuccess) {
                    successCount++
                } else {
                    Log.e(tag, "Échec de l'upload de l'image : $uri")
                }

                // Envoyer la progression avec le statut de CETTE image
                val isLastImageInLoop =
                    (index == uriStrings.size - 1) // Vérifier si c'est la dernière itération normale
                val progressData = workDataOf(
                    KEY_PROGRESS_UPLOAD_ID to uploadId,
                    KEY_PROGRESS_INDEX to index,
                    KEY_PROGRESS_URI to uriString,
                    KEY_PROGRESS_SUCCESS to isSuccess,
                    // On indique "isLast" seulement si on atteint la fin de la boucle *sans être stoppé*
                    KEY_PROGRESS_IS_LAST to (isLastImageInLoop && !isStopped)
                )
                setProgress(progressData) // Utiliser setProgress (synchrone dans CoroutineWorker)
                Log.d(
                    tag,
                    "Progression envoyée pour l'image $index (Succès: $isSuccess, Dernière dans boucle: ${isLastImageInLoop && !isStopped})"
                )


                // Vérifier à nouveau si le travail a été annulé après l'upload et l'envoi de la progression
                if (isStopped) {
                    Log.i(tag, "Travail annulé après avoir traité l'image $index")
                    break // Sortir de la boucle
                }
            } // Fin de la boucle for

            // Calculer les données de sortie finales
            val finalSuccessCount = successCount
            // Le nombre d'échecs est le total moins les succès, seulement si on n'a pas été stoppé
            val finalFailedCount =
                if (!isStopped) totalImages - successCount else uriStrings.size - successCount // Si stoppé, les non traités comptent comme failed pour le total.

            val outputData = workDataOf(
                KEY_OUTPUT_SUCCESS_COUNT to finalSuccessCount,
                KEY_OUTPUT_FAILED_COUNT to finalFailedCount,
                KEY_OUTPUT_TOTAL_COUNT to totalImages
            )

            Log.d(
                tag,
                "Worker terminé: finalSuccessCount=$finalSuccessCount, finalFailedCount=$finalFailedCount, totalCount=$totalImages, isStopped=$isStopped"
            )

            // Déterminer le résultat final basé sur l'état (isStopped a priorité)
            // IMPORTANT: Ne pas retourner ici si isStopped, laisser le WorkerManager gérer l'état CANCELLED
            return@withContext when {
                // isStopped -> Result.failure(outputData) // Ne pas retourner failure ici, WM le mettra en CANCELLED
                finalFailedCount > 0 && finalSuccessCount == 0 && !isStopped -> Result.failure(
                    outputData
                ) // Échec total (et non stoppé)
                finalFailedCount > 0 && !isStopped -> Result.success(outputData) // Succès partiel (et non stoppé)
                !isStopped -> Result.success(outputData) // Succès complet (et non stoppé)
                else -> {
                    // Ce cas couvre isStopped == true. WorkManager gère l'annulation.
                    // On retourne failure ici pour signaler que le travail n'a pas abouti comme prévu,
                    // mais l'état final observé sera CANCELLED.
                    Log.i(tag, "Travail stoppé, retour implicite failure/cancelled.")
                    Result.failure(outputData)
                }
            }

            // Le bloc catch correspondant au try principal
        } catch (e: Exception) {
            Log.e(tag, "Erreur inattendue dans le worker", e)
            // Retourner failure avec l'erreur dans le bloc catch
            return@withContext Result.failure(
                workDataOf(KEY_OUTPUT_ERROR to (e.message ?: "Erreur inconnue dans le worker"))
            )
        }
    }


    /**
     * Upload une seule image (Logique inchangée)
     */
    private suspend fun uploadSingleImage(uri: Uri, travelId: Long): Boolean {
        var tempFile: File? = null
        try {
            Log.d(tag, "Début uploadSingleImage pour URI: $uri")
            tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                Log.e(tag, "Échec de la création du fichier temporaire depuis URI: $uri")
                return false
            }
            Log.d(
                tag,
                "Fichier temporaire créé: ${tempFile.absolutePath}, taille: ${tempFile.length()}"
            )

            if (apiClient == null) {
                Log.e(tag, "ApiClient est null, impossible de faire l'upload")
                return false
            }

            val requestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = requestBody.let {
                MultipartBody.Part.createFormData("picture", UUID.randomUUID().toString(), it)
            }

            // Mais ensuite, pas besoin de construire un MultipartBody complet
            // Au lieu de tout ce bloc multipartBodyBuilder...
            // val multipartBodyBuilder = MultipartBody.Builder()...

            // Si vous avez besoin d'ajouter des métadonnées EXIF, utilisez des @Part supplémentaires
            val exifInterface = ExifInterface(tempFile)
            val tagsToCheck = arrayOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_ALTITUDE
            )

            // Créer une liste pour stocker toutes les parties
            val parts = mutableListOf<MultipartBody.Part>()
            parts.add(body) // Ajouter l'image

            // Ajouter les métadonnées EXIF si présentes
            for (tag in tagsToCheck) {
                exifInterface.getAttribute(tag)?.let { value ->
                    parts.add(MultipartBody.Part.createFormData("exif_$tag", value))
                }
            }

            Log.d(tag, "MultipartBody préparé, envoi de la requête")

            val response = apiClient!!.pictureService.uploadPicture(travelId, parts).execute()
            Log.d(
                tag,
                "Réponse reçue: isSuccessful=${response.isSuccessful}, code=${response.code()}"
            )

            // Gérer les codes d'erreur spécifiques si nécessaire
            if (!response.isSuccessful) {
                Log.e(
                    tag,
                    "Erreur API: Code=${response.code()}, Message=${response.message()}, Body=${
                        response.errorBody()?.string()
                    }"
                )
            }

            return response.isSuccessful

        } catch (e: Exception) {
            Log.e(tag, "Exception lors de l'upload de l'image $uri", e)
            return false
        } finally {
            try {
                tempFile?.delete()
                Log.d(tag, "Fichier temporaire supprimé pour $uri")
            } catch (e: Exception) {
                Log.e(tag, "Erreur lors de la suppression du fichier temporaire pour $uri", e)
            }
        }
    }

    /**
     * Crée un fichier temporaire à partir d'un URI (Logique inchangée)
     */
    private suspend fun createTempFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Création du fichier temporaire pour $uri")
            // Donner un nom de fichier un peu plus descriptif si possible
            val fileName = "upload_temp_${uri.lastPathSegment ?: System.currentTimeMillis()}.tmp"
            val tempFile = File(applicationContext.cacheDir, fileName)

            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output) // Utilisation de copyTo pour simplifier
                }
                Log.d(tag, "Copie terminée pour $uri vers ${tempFile.absolutePath}")
            } ?: run {
                Log.e(tag, "Impossible d'ouvrir l'InputStream pour l'URI: $uri")
                return@withContext null
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                // Vérifier la taille avant de logger pour éviter une exception si le fichier n'existe pas
                val fileSize = if (tempFile.exists()) tempFile.length() else -1L
                Log.e(
                    tag,
                    "Fichier temporaire invalide: existe=${tempFile.exists()}, taille=$fileSize"
                )
                // Tenter de supprimer un fichier invalide
                if (tempFile.exists()) tempFile.delete()
                return@withContext null
            }

            Log.d(
                tag,
                "Fichier temporaire créé avec succès: ${tempFile.absolutePath}, taille: ${tempFile.length()} octets"
            )
            return@withContext tempFile
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de la création du fichier temporaire pour $uri", e)
            // Tenter de supprimer le fichier s'il existe après une exception
            val tempFile = File(
                applicationContext.cacheDir,
                "upload_temp_${uri.lastPathSegment ?: System.currentTimeMillis()}.tmp"
            ) // recréer le path potentiel
            if (tempFile.exists()) {
                try {
                    tempFile.delete()
                } catch (_: Exception) {
                }
            }
            return@withContext null
        }
    }
}