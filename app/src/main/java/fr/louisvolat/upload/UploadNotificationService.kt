package fr.louisvolat.upload

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.louisvolat.R
import fr.louisvolat.upload.notification.UploadBroadcastReceiver
import fr.louisvolat.upload.state.UploadState
import fr.louisvolat.view.MainActivity

/**
 * Service de notification pour les uploads d'images
 * Respecte le principe de responsabilité unique (S de SOLID)
 */
class UploadNotificationService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "image_upload_channel"
        const val NOTIFICATION_ID = 2000
        const val ACTION_RETRY = "fr.louisvolat.ACTION_RETRY_UPLOAD"
        const val ACTION_CANCEL = "fr.louisvolat.ACTION_CANCEL_UPLOAD"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.upload_notification_channel_name)
        val description = context.getString(R.string.upload_notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(false)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Met à jour la notification en fonction de l'état d'upload actuel
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun updateNotification(state: UploadState) {
        when {
            state.isUploading -> showUploadProgressNotification(state)
            state.isComplete && !state.hasErrors -> showUploadCompletedNotification(state.uploadedImages)
            state.hasErrors -> showUploadFailedNotification(state.failedUris.size, state.totalImages)
            else -> cancelNotification()
        }
    }

    /**
     * Affiche une notification d'upload en cours avec une barre de progression
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showUploadProgressNotification(state: UploadState) {
        val builder = createBaseNotificationBuilder()
            .setContentTitle(context.getString(R.string.uploading_images))
            .setContentText("${state.progressText} (${state.progress}%)")
            .setProgress(100, state.progress, false)
            .setOngoing(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Affiche une notification d'upload terminé
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showUploadCompletedNotification(totalUploaded: Int) {
        val builder = createBaseNotificationBuilder()
            .setContentTitle(context.getString(R.string.upload_completed))
            .setContentText(context.getString(R.string.upload_success_message, totalUploaded))
            .setProgress(0, 0, false)
            .clearActions()
            .setSmallIcon(R.drawable.outlined_check_circle_24)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Affiche une notification d'échec d'upload avec option de réessayer
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showUploadFailedNotification(failedCount: Int, totalCount: Int) {
        // Intent pour réessayer
        val retryIntent = Intent(context, UploadBroadcastReceiver::class.java).apply {
            action = ACTION_RETRY
        }
        val retryPendingIntent = PendingIntent.getBroadcast(
            context, 0, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = createBaseNotificationBuilder()
            .setContentTitle(context.getString(R.string.upload_failed))
            .setContentText(context.getString(R.string.upload_failed_message, failedCount, totalCount))
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .addAction(
                R.drawable.outlined_upload_24,
                context.getString(R.string.retry),
                retryPendingIntent
            )

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Crée le builder de base pour les notifications
     */
    private fun createBaseNotificationBuilder(): NotificationCompat.Builder {
        // Intent pour ouvrir l'application
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent pour annuler
        val cancelIntent = Intent(context, UploadBroadcastReceiver::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.outlined_upload_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.outline_cancel_24,
                context.getString(R.string.cancel),
                cancelPendingIntent
            )
    }

    /**
     * Supprime la notification d'upload
     */
    fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}