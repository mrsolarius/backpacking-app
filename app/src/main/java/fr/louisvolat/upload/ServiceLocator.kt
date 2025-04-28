package fr.louisvolat.upload.di

import android.content.Context
import fr.louisvolat.upload.UploadNotificationService
import fr.louisvolat.upload.service.UploadImageService
import fr.louisvolat.upload.service.UploadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Service Locator pour fournir les dépendances
 * Pattern permettant d'implémenter l'injection de dépendances
 * Note: Dans une application plus complexe, il serait préférable d'utiliser Hilt ou Koin
 */
object ServiceLocator {

    private var uploadService: UploadService? = null
    private var notificationService: UploadNotificationService? = null

    /**
     * Fournit une instance unique du service d'upload
     */
    fun provideUploadService(context: Context): UploadService {
        return uploadService ?: synchronized(this) {
            uploadService ?: createUploadService(context.applicationContext).also {
                uploadService = it
            }
        }
    }

    /**
     * Fournit une instance unique du service de notification
     */
    fun provideNotificationService(context: Context): UploadNotificationService {
        return notificationService ?: synchronized(this) {
            notificationService ?: createNotificationService(context.applicationContext).also {
                notificationService = it
            }
        }
    }

    /**
     * Crée une nouvelle instance du service d'upload
     */
    private fun createUploadService(context: Context): UploadService {
        return UploadImageService(
            context = context,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            ioDispatcher = Dispatchers.IO
        )
    }

    /**
     * Crée une nouvelle instance du service de notification
     */
    private fun createNotificationService(context: Context): UploadNotificationService {
        return UploadNotificationService(context)
    }
}