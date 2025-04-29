package fr.louisvolat.data.repository

import android.content.ContentResolver
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import fr.louisvolat.api.ApiClient
import fr.louisvolat.api.dto.PictureDTO
import fr.louisvolat.data.mapper.PictureMapper
import fr.louisvolat.database.dao.PictureDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class PictureRepository(
    private val pictureDao: PictureDao,
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // Méthode pour télécharger une image uniquement
    suspend fun uploadPicture(context: Context, uri: Uri, travelId: Long): Result<PictureDTO> =
        withContext(ioDispatcher) {
            try {
                val contentResolver: ContentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Could not open input stream for URI: $uri"))

                // Crée un fichier temporaire
                val tempFile =
                    File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")

                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                // Créer le MultipartBody.Part pour l'image
                val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart =
                    MultipartBody.Part.createFormData("picture", tempFile.name, requestFile)

                // Créer une liste pour stocker toutes les parties
                val parts = mutableListOf<MultipartBody.Part>()
                parts.add(imagePart) // Ajouter l'image

                // Ajouter les métadonnées EXIF si présentes
                val exifInterface = ExifInterface(tempFile)
                val tagsToCheck = arrayOf(
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE
                )

                for (tag in tagsToCheck) {
                    exifInterface.getAttribute(tag)?.let { value ->
                        // Créer un RequestBody pour la valeur EXIF
                        val exifBody = value.toRequestBody("text/plain".toMediaTypeOrNull())
                        // Créer un MultipartBody.Part pour cette valeur EXIF
                        val exifPart = MultipartBody.Part.createFormData("exif_$tag", value)
                        parts.add(exifPart)
                    }
                }

                // Appeler la méthode avec la liste de parts
                val response = apiClient.pictureService.uploadPicture(travelId, parts).execute()

                if (response.isSuccessful) {
                    val pictureDTO = response.body()
                    pictureDTO?.let {
                        // Sauvegarde de l'image dans la BDD locale
                        val pictureEntity = PictureMapper.toEntityWithLocalPathAndTravelId(
                            it,
                            uri.toString(),
                            travelId
                        )
                        pictureDao.insert(pictureEntity)
                        Result.success(it)
                    } ?: Result.failure(Exception("Empty response body"))
                } else {
                    Result.failure(Exception("API error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // Nouvelle méthode pour définir une image comme couverture
    suspend fun setCoverPicture(travelId: Long, pictureId: Long): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val response =
                    apiClient.pictureService.setCoverPicture(travelId, pictureId).execute()

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("API error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}