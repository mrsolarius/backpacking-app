package fr.louisvolat.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.louisvolat.upload.UploadNotificationManager.Companion.ACTION_CANCEL
import fr.louisvolat.upload.UploadNotificationManager.Companion.ACTION_RETRY

/**
 * BroadcastReceiver pour gérer les actions de notification d'upload
 */
class UploadBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val uploadManager = ImageUploadManager.getInstance(context)

        when (intent.action) {
            ACTION_RETRY -> {
                uploadManager.retryFailedUploads()
            }
            ACTION_CANCEL -> {
                uploadManager.cancelAllUploads()
                UploadNotificationManager(context).cancelNotification()
            }
        }
    }
}