package fr.louisvolat.upload.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.Observer // Importer Observer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import fr.louisvolat.upload.state.UploadState
import fr.louisvolat.worker.UploadImageWorker // Importer pour les constantes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update // Utiliser update pour la modification thread-safe
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Implémentation du service d'upload qui coordonne les uploads d'images via WorkManager
 * et met à jour son état en observant WorkInfo (y compris la progression).
 */
class UploadImageService(
    private val context: Context,
    // Utiliser un scope fourni ou créer un scope qui peut être annulé
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UploadService {

    companion object {
        private const val TAG = "UploadImageService"
        const val UPLOAD_WORK_NAME = "image_upload_work"
        const val MAX_QUEUE_SIZE = 100

        // Clés pour les données d'entrée du worker (INPUT)
        const val KEY_UPLOAD_ID = "upload_id"
        const val KEY_URI_LIST = "uri_list"
        const val KEY_TRAVEL_ID = "travel_id"
        // KEY_TOTAL_IMAGES n'est plus envoyé en input car le worker utilise uriStrings.size
        // const val KEY_TOTAL_IMAGES = "total_images"
        // KEY_CURRENT_INDEX n'est plus envoyé en input
        // const val KEY_CURRENT_INDEX = "current_index"
    }

    private val workManager = WorkManager.getInstance(context)
    private var observerJob: Job? = null // Pour gérer l'observation
    private val workInfoObserver = Observer<List<WorkInfo>> { workInfoList ->
        handleWorkInfoUpdate(workInfoList)
    }


    // État actuel de l'upload
    private val _uploadState = MutableStateFlow(UploadState())
    override val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * Démarre l'upload de plusieurs images
     */
    override suspend fun uploadImages(uris: List<Uri>, travelId: Long): String = withContext(ioDispatcher) {
        if (uris.isEmpty()) return@withContext ""

        Log.d(TAG, "Début uploadImages avec ${uris.size} URIs pour travelId $travelId")

        // Limiter la taille de la file d'attente
        val limitedUris = if (uris.size > MAX_QUEUE_SIZE) {
            Log.w(TAG,"Nombre d'URIs (${uris.size}) dépasse la limite ($MAX_QUEUE_SIZE), troncage.")
            uris.take(MAX_QUEUE_SIZE)
        } else uris
        Log.d(TAG, "URIs limitées à ${limitedUris.size}")

        // Créer un ID unique pour cet ensemble d'uploads
        val uploadId = java.util.UUID.randomUUID().toString()
        Log.d(TAG, "Upload ID généré: $uploadId")

        // Trier les URI par taille de fichier (de la plus petite à la plus grande)
        val sortedUris = limitedUris.sortedBy { getFileSize(context, it) }
        Log.d(TAG, "URIs triées par taille")

        // Mettre à jour l'état initial avant d'enqueue
        _uploadState.value = UploadState(
            isUploading = true,
            totalImages = sortedUris.size,
            uploadedImages = 0,
            currentImageIndex = -1, // Commencer à -1 ou 0 ? Mettons -1 pour indiquer "pas encore commencé"
            pendingUris = sortedUris,
            failedUris = emptyList(), // Réinitialiser les échecs
            uploadId = uploadId,
            travelId = travelId,
            error = null // Réinitialiser les erreurs
        )
        Log.d(TAG, "État initial défini: totalImages=${sortedUris.size}, uploadId=$uploadId")

        // Préparer les données pour le worker
        val inputData = Data.Builder()
            .putString(KEY_UPLOAD_ID, uploadId)
            .putStringArray(KEY_URI_LIST, sortedUris.map { it.toString() }.toTypedArray())
            .putLong(KEY_TRAVEL_ID, travelId)
            // Ne plus passer total/index ici
            .build()

        // Définir les contraintes pour l'exécution du travail
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // .setRequiresBatteryNotLow(true) // Peut-être trop restrictif ? A évaluer.
            .build()

        // Créer la requête de travail
        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadImageWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            // Ajouter un tag peut être utile pour l'observation si UPLOAD_WORK_NAME n'est pas suffisant
            // .addTag(UPLOAD_WORK_TAG)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS, // Utiliser la constante de WorkManager
                TimeUnit.MILLISECONDS
            )
            .build()

        Log.d(TAG, "Envoi de la requête au WorkManager avec ID de travail: ${uploadWorkRequest.id} et Nom Unique: $UPLOAD_WORK_NAME")

        // S'assurer que l'observation est active
        observeWorkInfo()

        // Envoyer la requête au WorkManager avec REPLACE pour éviter les conflits
        // Utiliser enqueueUniqueWork pour gérer un seul upload à la fois
        workManager.enqueueUniqueWork(
            UPLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE, // Remplacer toute tâche existante avec ce nom
            uploadWorkRequest
        )

        Log.d(TAG, "Requête envoyée au WorkManager")

        return@withContext uploadId
    }

    /**
     * Annule tous les uploads en cours associés à ce nom unique
     */
    override suspend fun cancelAllUploads() = withContext(Dispatchers.Main) { // S'assurer d'être sur le Main thread pour màj UI state?
        Log.d(TAG, "Demande d'annulation du travail unique : $UPLOAD_WORK_NAME")
        workManager.cancelUniqueWork(UPLOAD_WORK_NAME)

        // Mettre à jour l'état immédiatement pour refléter l'action utilisateur
        // L'état final (CANCELLED) sera confirmé par l'observateur WorkInfo plus tard
        _uploadState.update { currentState ->
            // Si un upload était effectivement en cours avec cet ID
            if (currentState.isUploading) {
                currentState.copy(
                    isUploading = false,
                    // Garder les échecs précédents, considérer les pending comme non uploadés
                    // failedUris = currentState.failedUris + currentState.pendingUris,
                    // pendingUris = emptyList(), // Vider les pending
                    error = "Annulation demandée par l'utilisateur" // Message temporaire
                )
            } else {
                currentState // Pas de changement si aucun upload n'était en cours
            }
        }
        Log.d(TAG,"État mis à jour après demande d'annulation (attente confirmation)")
        return@withContext Unit
    }

    /**
     * Réessaie les uploads qui ont échoué lors du dernier batch.
     */
    override suspend fun retryFailedUploads() {
        val stateToRetry = _uploadState.value
        Log.d(TAG, "Tentative de réessayer ${stateToRetry.failedUris.size} uploads échoués pour travelId ${stateToRetry.travelId}")

        if (stateToRetry.failedUris.isNotEmpty() && stateToRetry.travelId != -1L) {
            // Relancer uploadImages UNIQUEMENT avec les URIs échouées
            coroutineScope.launch { // Use coroutineScope.launch
                uploadImages(stateToRetry.failedUris, stateToRetry.travelId)
            }
        } else {
            Log.w(TAG, "Aucun upload échoué à réessayer ou travelId manquant.")
        }
    }

    // Méthode interne pour traiter les mises à jour de WorkInfo
    private fun handleWorkInfoUpdate(workInfoList: List<WorkInfo>?) {
        if (workInfoList.isNullOrEmpty()) {
            Log.d(TAG,"Liste WorkInfo vide ou nulle reçue.")
            return
        }

        // Normalement, avec enqueueUniqueWork, il ne devrait y avoir qu'un seul WorkInfo
        val workInfo = workInfoList[0]
        val currentUploadId = _uploadState.value.uploadId

        Log.d(TAG, "handleWorkInfoUpdate: WorkID=${workInfo.id}, State=${workInfo.state}, UploadID_Service=$currentUploadId")


        // Traiter la progression si le worker est en cours ET si l'ID correspond
        if (workInfo.state == WorkInfo.State.RUNNING) {
            val progressData = workInfo.progress
            val progressUploadId = progressData.getString(UploadImageWorker.KEY_PROGRESS_UPLOAD_ID)

            // Vérifier si la progression concerne bien l'upload actuel géré par le service
            if (progressUploadId == currentUploadId) {
                updateStateFromProgress(progressData)
            } else {
                Log.w(TAG,"Progression reçue pour un uploadId ($progressUploadId) différent de l'actuel ($currentUploadId). Ignorée.")
            }
        }

        // Gérer les états finaux (Succès, Échec, Annulé)
        when (workInfo.state) {
            WorkInfo.State.SUCCEEDED -> {
                Log.i(TAG, "WorkInfo SUCCEEDED pour WorkID: ${workInfo.id}")
                val outputData = workInfo.outputData
                val successCount = outputData.getInt(UploadImageWorker.KEY_OUTPUT_SUCCESS_COUNT, _uploadState.value.uploadedImages) // Utiliser état courant comme fallback
                val failedCount = outputData.getInt(UploadImageWorker.KEY_OUTPUT_FAILED_COUNT, _uploadState.value.failedUris.size)
                val totalCount = outputData.getInt(UploadImageWorker.KEY_OUTPUT_TOTAL_COUNT, _uploadState.value.totalImages)

                _uploadState.update { currentState ->
                    // Vérifier si cela concerne l'upload actuel
                    if (currentState.uploadId == currentUploadId && currentState.isUploading) {
                        currentState.copy(
                            isUploading = false,
                            // Assurer la cohérence finale, même si la progression a été manquée
                            uploadedImages = successCount,
                            failedUris = if(failedCount > 0 && currentState.failedUris.isEmpty()) {
                                // Tenter de reconstituer les échecs si l'info n'est pas passée par la progression
                                Log.w(TAG,"Succès final avec $failedCount échecs, mais liste failedUris vide. Incohérence possible.")
                                // On ne peut pas savoir *quelles* URIs ont échoué ici.
                                currentState.failedUris // Garder ce qui existe déjà
                            } else currentState.failedUris,
                            pendingUris = emptyList(), // Tout a été traité
                            error = if (failedCount > 0) "Échec de l'upload de $failedCount image(s) sur $totalCount" else null
                        )
                    } else {
                        currentState // Ne pas modifier si ce n'est pas l'upload actuel ou déjà terminé
                    }
                }
                Log.d(TAG, "État final SUCCEEDED: success=$successCount, failed=$failedCount, total=$totalCount")
            }
            WorkInfo.State.FAILED -> {
                Log.e(TAG, "WorkInfo FAILED pour WorkID: ${workInfo.id}")
                val outputData = workInfo.outputData
                val successCount = outputData.getInt(UploadImageWorker.KEY_OUTPUT_SUCCESS_COUNT, _uploadState.value.uploadedImages)
                val failedCount = outputData.getInt(UploadImageWorker.KEY_OUTPUT_FAILED_COUNT, _uploadState.value.failedUris.size)
                val totalCount = outputData.getInt(UploadImageWorker.KEY_OUTPUT_TOTAL_COUNT, _uploadState.value.totalImages)
                val errorMessage = outputData.getString(UploadImageWorker.KEY_OUTPUT_ERROR) ?: "Erreur inconnue du worker"

                _uploadState.update { currentState ->
                    if (currentState.uploadId == currentUploadId && currentState.isUploading) {
                        currentState.copy(
                            isUploading = false,
                            uploadedImages = successCount, // Mettre à jour si certains ont réussi avant l'échec global
                            // Considérer tous les pending comme failed en cas d'échec global ? Ou se fier au compte ?
                            failedUris = if(currentState.failedUris.size != failedCount) {
                                Log.w(TAG,"Échec final avec $failedCount échecs, mais liste failedUris contient ${currentState.failedUris.size} éléments. Incohérence possible.")
                                currentState.failedUris // Difficile de savoir lesquelles ont échoué ici
                            } else currentState.failedUris,
                            pendingUris = emptyList(),
                            error = "Échec de l'upload: $errorMessage ($failedCount / $totalCount échoués)"
                        )
                    } else {
                        currentState
                    }
                }
                Log.d(TAG, "État final FAILED: message=$errorMessage, success=$successCount, failed=$failedCount, total=$totalCount")
            }
            WorkInfo.State.CANCELLED -> {
                Log.w(TAG, "WorkInfo CANCELLED pour WorkID: ${workInfo.id}")
                // L'état a peut-être déjà été mis à jour lors de l'appel à cancelAllUploads
                // Mais confirmer ici l'état final
                _uploadState.update { currentState ->
                    if (currentState.uploadId == currentUploadId && currentState.isUploading) {
                        currentState.copy(
                            isUploading = false,
                            // Les URIs restantes dans pending sont considérées comme non uploadées car annulées.
                            failedUris = currentState.failedUris + currentState.pendingUris, // Ajouter les pending aux failed
                            pendingUris = emptyList(),
                            error = "Upload annulé" // Confirmer le message d'erreur
                        )
                    } else {
                        // Si déjà annulé ou pas le bon ID, ne rien faire, ou juste s'assurer que isUploading est false
                        if (currentState.uploadId == currentUploadId) currentState.copy(isUploading = false, error = currentState.error ?: "Upload annulé") else currentState
                    }
                }
                Log.d(TAG, "État final CANCELLED confirmé.")
            }
            WorkInfo.State.ENQUEUED -> Log.d(TAG, "WorkInfo ENQUEUED pour WorkID: ${workInfo.id}")
            WorkInfo.State.BLOCKED -> Log.d(TAG, "WorkInfo BLOCKED pour WorkID: ${workInfo.id}")
            else -> Log.d(TAG, "WorkInfo état non géré explicitement pour la fin: ${workInfo.state}")
        }

        // Optionnel : Arrêter l'observation si le travail est terminé définitivement
        if (workInfo.state.isFinished) {
            Log.d(TAG, "Le travail ${workInfo.id} est terminé (${workInfo.state}). L'observation pourrait être arrêtée si nécessaire.")
            // removeObserver() // Attention si on veut observer de futurs uploads
        }
    }

    // Méthode interne pour mettre à jour l'état basé sur la progression reçue
    private fun updateStateFromProgress(progressData: Data) {
        val index = progressData.getInt(UploadImageWorker.KEY_PROGRESS_INDEX, -1)
        val uriString = progressData.getString(UploadImageWorker.KEY_PROGRESS_URI)
        val success = progressData.getBoolean(UploadImageWorker.KEY_PROGRESS_SUCCESS, false)
        val isLast = progressData.getBoolean(UploadImageWorker.KEY_PROGRESS_IS_LAST, false) // Récupérer l'info "isLast"

        if (index == -1 || uriString == null) {
            Log.e(TAG, "Données de progression invalides reçues: index=$index, uriString=$uriString")
            return
        }

        val uri = uriString.toUri()
        Log.d(TAG, "Progression reçue: index=$index, uri=$uriString, success=$success, isLast=$isLast")


        _uploadState.update { currentState ->
            // S'assurer qu'on met à jour l'état pertinent
            if (!currentState.isUploading) {
                Log.w(TAG, "Progression reçue alors que l'état n'est pas 'isUploading'. Ignorée.")
                return@update currentState // Ne rien changer si on n'est plus en mode upload
            }

            val newPendingUris = currentState.pendingUris.toMutableList()
            val newFailedUris = currentState.failedUris.toMutableList()
            var newUploadedCount = currentState.uploadedImages

            val removed = newPendingUris.remove(uri)
            if (!removed) {
                Log.w(TAG, "URI $uri de la progression non trouvée dans pendingUris.")
            }

            if (success) {
                newUploadedCount++
                // S'assurer qu'on ne retire pas des failed si ça réussit après un retry (logique de retry à revoir si nécessaire)
                newFailedUris.remove(uri)
            } else {
                // Ajouter aux échecs seulement si pas déjà dedans
                if (!newFailedUris.contains(uri)) {
                    newFailedUris.add(uri)
                }
            }

            // Calculer si l'upload est toujours en cours
            // Utiliser isLast reçu du worker pour déterminer la fin plus précisément
            val stillUploading = !isLast

            Log.d(TAG, "Mise à jour état: uploaded=$newUploadedCount, pending=${newPendingUris.size}, failed=${newFailedUris.size}, index=$index, stillUploading=$stillUploading (basé sur isLast=$isLast)")

            currentState.copy(
                uploadedImages = newUploadedCount,
                currentImageIndex = index, // Mettre à jour l'index traité
                pendingUris = newPendingUris,
                failedUris = newFailedUris,
                isUploading = stillUploading, // Mettre à jour isUploading basé sur isLast
                // Ne pas effacer l'erreur ici, elle sera gérée par les états finaux
                error = if (!stillUploading && newFailedUris.isNotEmpty()) currentState.error ?: "Des erreurs sont survenues" else currentState.error // Message temporaire si erreurs à la fin
            )
        }
    }


    /**
     * Démarre l'observation des WorkInfo si ce n'est pas déjà fait.
     * Utilise observeForever, nécessite une gestion manuelle du cycle de vie si le service est lié à un composant.
     */
    private fun observeWorkInfo() {
        // Lancer l'observateur seulement s'il n'est pas déjà actif
        // Note: Dans un vrai service Android, lier cela au cycle de vie du service.
        // Ici, on le lance une fois et on compte sur le scope pour l'annulation.
        if (observerJob == null || !observerJob!!.isActive) {
            observerJob = coroutineScope.launch(Dispatchers.Main) { // Observer sur le Main thread
                try {
                    Log.d(TAG, "Démarrage de l'observation de WorkInfo pour $UPLOAD_WORK_NAME")
                    // Utiliser LiveData ici car WorkManager le fournit facilement
                    // Pour une approche pure Flow, il faudrait utiliser getWorkInfosForUniqueWorkFlow
                    workManager.getWorkInfosForUniqueWorkLiveData(UPLOAD_WORK_NAME)
                        .observeForever(workInfoObserver)
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors du démarrage de l'observation WorkInfo", e)
                }
            }
            // Gérer l'arrêt de l'observation lorsque le scope est annulé
            observerJob?.invokeOnCompletion {
                Log.d(TAG, "Arrêt de l'observation de WorkInfo pour $UPLOAD_WORK_NAME car le scope est complété.")
                // S'assurer de retirer l'observateur pour éviter les fuites
                // Ceci doit être fait sur le Main thread si l'ajout l'a été
                coroutineScope.launch(Dispatchers.Main) {
                    workManager.getWorkInfosForUniqueWorkLiveData(UPLOAD_WORK_NAME)
                        .removeObserver(workInfoObserver)
                }
            }
        } else {
            Log.d(TAG,"Observation WorkInfo déjà active.")
        }

    }

    // Optionnel: Méthode pour arrêter l'observation manuellement si nécessaire
    fun stopObserving() {
        if (observerJob?.isActive == true) {
            Log.d(TAG, "Demande d'arrêt manuel de l'observation.")
            // L'annulation du job déclenchera invokeOnCompletion qui retire l'observer
            observerJob?.cancel()
        }
        // On pourrait aussi retirer l'observer directement ici sur le Main thread
        // GlobalScope.launch(Dispatchers.Main) { // Utiliser un scope approprié
        //     workManager.getWorkInfosForUniqueWorkLiveData(UPLOAD_WORK_NAME)
        //                .removeObserver(workInfoObserver)
        // }
        observerJob = null
    }


    /**
     * Obtient la taille d'un fichier à partir de son URI (Inchangé)
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            // Logguer l'erreur peut être utile
            Log.e(TAG, "Impossible d'obtenir la taille pour l'URI: $uri", e)
            0L // Retourner 0 en cas d'erreur
        }
    }

    // Suppression de la méthode `updateUploadState` publique/interface car non utilisée
    // override suspend fun updateUploadState(...) { ... }
}