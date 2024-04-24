package fr.louisvolat.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class UploadImageWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val uris = inputData.getStringArray("uris") ?: return Result.failure()

        val client = okhttp3.OkHttpClient()

        for (uriString in uris) {
            val uri = Uri.parse(uriString)
            val uuid = UUID.randomUUID().toString()
            val file = File(applicationContext.cacheDir, uuid)
            uri?.let { applicationContext.contentResolver.openInputStream(it) }.use { input ->
                file.outputStream().use { output ->
                    input?.copyTo(output)
                }
            }
            val inputStream = file.inputStream()
            val requestBody = inputStream.readBytes().toRequestBody("image/*".toMediaTypeOrNull())
            val body = requestBody.let {
                MultipartBody.Part.createFormData("picture", UUID.randomUUID().toString(), it)
            }

            val multipartBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(body)

            val exifInterface = ExifInterface(file)

            val tagsToCheck = arrayOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_ALTITUDE
            )

            for (tag in tagsToCheck) {
                exifInterface.getAttribute(tag)
                    ?.let { multipartBodyBuilder.addFormDataPart("exif_$tag", it) }
            }
            val multipartBody = multipartBodyBuilder.build()

            file.delete()

            val request = okhttp3.Request.Builder()
                .url("https://api.backpaking.louisvolat.fr/api/pictures")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.d("error", errorBody)
                return Result.failure()
            }
        }

        return Result.success()
    }
}