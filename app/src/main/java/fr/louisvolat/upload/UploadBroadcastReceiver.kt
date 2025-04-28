package fr.louisvolat.upload.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.louisvolat.upload.UploadNotificationService.Companion.ACTION_RETRY
import fr.louisvolat.upload.UploadNotificationService.Companion.ACTION_CANCEL
import fr.louisvolat.upload.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver pour gérer les actions de notification d'upload
 * Respecte le principe de substitution de Liskov (L de SOLID)
 */
class UploadBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Utiliser le ServiceLocator pour obtenir le service d'upload
        val uploadService = ServiceLocator.provideUploadService(context)
        val notificationService = ServiceLocator.provideNotificationService(context)

        when (intent.action) {
            ACTION_RETRY -> {
                // Utiliser une coroutine pour exécuter les opérations suspendues
                kotlinx.coroutines.GlobalScope.launch {
                    uploadService.retryFailedUploads()
                }
            }
            ACTION_CANCEL -> {
                // Utiliser une coroutine pour exécuter les opérations suspendues
                kotlinx.coroutines.GlobalScope.launch {
                    uploadService.cancelAllUploads()
                    notificationService.cancelNotification()
                }
            }
        }
    }
}