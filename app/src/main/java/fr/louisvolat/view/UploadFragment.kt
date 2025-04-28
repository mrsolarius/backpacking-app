package fr.louisvolat.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.repository.PictureRepository
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.data.viewmodel.TravelViewModel
import fr.louisvolat.data.viewmodel.TravelViewModelFactory
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.databinding.FragmentUploadBinding
import fr.louisvolat.upload.ImageUploadManager
import fr.louisvolat.upload.UploadNotificationManager
import fr.louisvolat.upload.UploadState

class UploadFragment : Fragment() {
    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!

    private lateinit var uploadManager: ImageUploadManager
    private lateinit var notificationManager: UploadNotificationManager

    private var travelId: Long = -1L

    // Lanceurs pour les permissions
    private lateinit var pickImagesLauncher: ActivityResultLauncher<String>
    private lateinit var notificationPermissionRequest: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uploadManager = ImageUploadManager.getInstance(requireContext())
        notificationManager = UploadNotificationManager(requireContext())

        // Initialiser les lanceurs pour les permissions
        registerPermissionLaunchers()
    }

    private fun registerPermissionLaunchers() {
        // Launcher pour sélectionner des images
        pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                startImageUpload(uris)
            }
        }

        // Launcher pour la permission de notification
        notificationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission accordée, lancer la sélection d'images
                pickImagesLauncher.launch("image/*")
            } else {
                // La permission de notification a été refusée
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showPermissionExplanationDialog()
                } else {
                    showPermissionSettingsDialog()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Si pas d'ID de voyage dans les arguments, essayer de le récupérer du ViewModel parent
        val database = BackpakingLocalDataBase.getDatabase(requireContext())
        val pictureRepository = PictureRepository(
            database.pictureDao(),
            ApiClient.getInstance(requireContext()),
        )
        val travelViewModel = ViewModelProvider(
            requireActivity(),
            TravelViewModelFactory(
                TravelRepository(
                    database.travelDao(),
                    database.pictureDao(),
                    ApiClient.getInstance(requireContext()),
                    pictureRepository
                )
            )
        )[TravelViewModel::class.java]

        travelViewModel.selectedTravelId.observe(viewLifecycleOwner) {
            travelId = it
            Log.d("UploadFragment", "TravelId mis à jour: $travelId")
        }

        setupListeners()
        observeUploadState()
        observeWorkInfo()
    }

    private fun setupListeners() {
        binding.btnUploadImages.setOnClickListener {
            // Vérifier si un voyage est sélectionné
            if (travelId == -1L) {
                binding.tvUploadStatus.text = "Veuillez d'abord sélectionner un voyage"
                return@setOnClickListener
            }

            // Vérifier les permissions avant de lancer la sélection d'images
            checkNotificationPermission()
        }

        binding.btnCancelUpload.setOnClickListener {
            uploadManager.cancelAllUploads()
        }

        binding.btnRetryUpload.setOnClickListener {
            uploadManager.retryFailedUploads()
        }
    }

    private fun checkNotificationPermission() {
        // Vérifier si la permission de notification est déjà accordée
        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasNotificationPermission) {
            // Permission déjà accordée, lancer la sélection d'images
            pickImagesLauncher.launch("image/*")
        } else {
            // Demander la permission de notification
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun observeUploadState() {
        uploadManager.uploadState.observe(viewLifecycleOwner) { state ->
            updateUI(state)

            // Mettre à jour la notification si on a la permission
            if (state.isUploading && ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.showUploadProgressNotification(state)
            }
        }
    }

    private fun observeWorkInfo() {
        uploadManager.getWorkInfoLiveData().observe(viewLifecycleOwner) { workInfoList ->
            if (workInfoList.isNullOrEmpty()) return@observe

            val workInfo = workInfoList[0]

            // Vérifier si on a la permission d'envoyer des notifications
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) return@observe

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val successCount = workInfo.outputData.getInt("success_count", 0)
                    val failedCount = workInfo.outputData.getInt("failed_count", 0)

                    if (failedCount > 0) {
                        notificationManager.showUploadFailedNotification(
                            failedCount,
                            successCount + failedCount
                        )
                    } else {
                        notificationManager.showUploadCompletedNotification(successCount)
                    }
                }
                WorkInfo.State.FAILED -> {
                    val failedCount = workInfo.outputData.getInt("failed_count", 0)
                    val totalCount = workInfo.outputData.getInt("total_count", 0)

                    notificationManager.showUploadFailedNotification(failedCount, totalCount)
                }
                else -> {
                    // Les autres états sont gérés par l'observation de l'état d'upload
                }
            }
        }
    }

    private fun updateUI(state: UploadState) {
        with(binding) {
            // Mettre à jour la barre de progression
            progressUpload.progress = state.progress
            tvUploadProgress.text = "${state.progressText} (${state.progress}%)"

            // Mettre à jour le texte de statut
            tvUploadStatus.text = when {
                state.isUploading -> "Téléchargement en cours..."
                state.isComplete -> "Téléchargement terminé"
                state.failedUris.isNotEmpty() -> "Échec du téléchargement de ${state.failedUris.size} image(s)"
                else -> "Prêt à télécharger"
            }

            // Gérer la visibilité des boutons et contrôles
            progressUpload.visibility = if (state.isUploading) View.VISIBLE else View.GONE
            tvUploadProgress.visibility = if (state.isUploading) View.VISIBLE else View.GONE
            btnCancelUpload.visibility = if (state.isUploading) View.VISIBLE else View.GONE
            btnRetryUpload.visibility = if (state.failedUris.isNotEmpty()) View.VISIBLE else View.GONE
            btnUploadImages.isEnabled = !state.isUploading
        }
    }

    private fun startImageUpload(uris: List<Uri>) {
        if (uris.isEmpty()) return

        uploadManager.uploadImages(uris, travelId)
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission nécessaire")
            .setMessage("Cette fonctionnalité nécessite la permission de notification pour vous tenir informé de l'avancement des téléchargements.")
            .setPositiveButton("Réessayer") { _, _ -> checkNotificationPermission() }
            .setNegativeButton("Annuler") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission refusée")
            .setMessage("La permission de notification a été refusée définitivement. Veuillez l'activer dans les paramètres de l'application pour utiliser pleinement cette fonctionnalité.")
            .setPositiveButton("Paramètres") { _, _ ->
                // Ouvrir les paramètres de l'application
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Annuler") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}