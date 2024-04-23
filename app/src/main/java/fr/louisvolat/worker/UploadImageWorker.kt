package fr.louisvolat.worker

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class UploadImageWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val uris = inputData.getStringArray("uris") ?: return Result.failure()

        val client = okhttp3.OkHttpClient()

        for (uriString in uris) {
            val uri = Uri.parse(uriString)
            val inputStream = applicationContext.contentResolver.openInputStream(uri)
            val requestBody = inputStream?.readBytes()?.toRequestBody("image/*".toMediaTypeOrNull())
            val body = requestBody?.let {
                MultipartBody.Part.createFormData("picture", UUID.randomUUID().toString(), it)
            } ?: return Result.failure()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(body)
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://api.backpaking.louisvolat.fr/api/pictures")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure()
            }
        }

        return Result.success()
    }
}