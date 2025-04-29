package fr.louisvolat.view

import UploadImageViewModelFactory
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fr.louisvolat.R
import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.repository.PictureRepository
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.data.viewmodel.TravelViewModel
import fr.louisvolat.data.viewmodel.TravelViewModelFactory
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.databinding.FragmentUploadBinding
import fr.louisvolat.upload.di.ServiceLocator
import fr.louisvolat.upload.UploadNotificationService
import fr.louisvolat.upload.state.UploadState
import fr.louisvolat.upload.viewmodel.UploadImageViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UploadFragment : Fragment() {
    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!

    // ViewModel pour l'upload, injecté via Factory et ServiceLocator
    private val uploadViewModel: UploadImageViewModel by lazy {
        val factory =
            UploadImageViewModelFactory(ServiceLocator.provideUploadService(requireContext()))
        ViewModelProvider(this, factory)[UploadImageViewModel::class.java]
    }

    // Service de notification obtenu via ServiceLocator
    private val notificationService: UploadNotificationService by lazy {
        ServiceLocator.provideNotificationService(requireContext())
    }

    // ViewModel partagé pour obtenir l'ID du voyage sélectionné
    private val travelViewModel: TravelViewModel by activityViewModels {
        val database = BackpakingLocalDataBase.getDatabase(requireContext())
        val pictureRepository = PictureRepository(
            database.pictureDao(),
            ApiClient.getInstance(requireContext()),
        )
        TravelViewModelFactory(
            TravelRepository(
                database.travelDao(),
                database.pictureDao(),
                ApiClient.getInstance(requireContext()),
                pictureRepository
            )
        )
    }

    private var travelId: Long = -1L

    // Lanceurs pour les permissions et la sélection d'images
    private lateinit var pickImagesLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionRequest: ActivityResultLauncher<String>
    private lateinit var storagePermissionRequest: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPermissionLaunchers()
    }

    private fun registerPermissionLaunchers() {
        // Launcher pour sélectionner des images depuis MediaStore (pour avoir les EXIF)
        pickImagesLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uris = mutableListOf<Uri>()

                    // Traiter les résultats (sélection multiple)
                    result.data?.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    } ?: result.data?.data?.let { uri ->
                        // Sélection unique
                        uris.add(uri)
                    }

                    if (uris.isNotEmpty()) {
                        startImageUpload(uris)
                    }
                }
            }

        // Launcher pour la permission de notification (requise pour Android 13+)
        notificationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission accordée, vérifier maintenant la permission de stockage
                checkStoragePermissionAndPickImages()
            } else {
                // La permission de notification a été refusée
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showPermissionExplanationDialog(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    showPermissionSettingsDialog(getString(R.string.notification_permission_settings_explanation))
                }
            }
        }

        // Launcher pour la permission d'accès au stockage externe
        storagePermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission accordée, lancer la sélection d'images via MediaStore
                launchMediaStorePicker()
            } else {
                // La permission de stockage a été refusée
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    showPermissionExplanationDialog(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    showPermissionSettingsDialog(getString(R.string.storage_permission_settings_explanation))
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

        // Observer l'ID du voyage sélectionné
        travelViewModel.selectedTravelId.observe(viewLifecycleOwner) { selectedId ->
            travelId = selectedId ?: -1L // Utiliser -1L si null
            Log.d("UploadFragment", "TravelId mis à jour: $travelId")
            // Mettre à jour l'état du bouton upload si l'ID change pendant que le fragment est visible
            binding.btnUploadImages.isEnabled =
                travelId != -1L && uploadViewModel.uploadState.value?.isUploading != true
        }

        setupListeners()
        observeUploadState()
    }

    private fun setupListeners() {
        binding.btnUploadImages.setOnClickListener {
            // Vérifier si un voyage est sélectionné
            if (travelId == -1L) {
                binding.tvUploadStatus.text = getString(R.string.select_travel_first)
                return@setOnClickListener
            }
            // Vérifier les permissions avant de lancer la sélection d'images
            checkNotificationPermissionAndPickImages()
        }

        binding.btnCancelUpload.setOnClickListener {
            uploadViewModel.cancelAllUploads()
        }

        binding.btnRetryUpload.setOnClickListener {
            // Vérifier si un voyage est sélectionné avant de réessayer
            if (travelId != -1L) {
                uploadViewModel.retryFailedUploads()
            } else {
                binding.tvUploadStatus.text = getString(R.string.select_travel_for_retry)
            }
        }
    }

    private fun checkNotificationPermissionAndPickImages() {
        // La permission n'est requise qu'à partir d'Android 13 (TIRAMISU)
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission accordée, vérifier la permission de stockage
                checkStoragePermissionAndPickImages()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // Afficher une explication
                showPermissionExplanationDialog(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> {
                // Demander la permission directement
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkStoragePermissionAndPickImages() {
        val permissionToCheck = Manifest.permission.READ_MEDIA_IMAGES

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permissionToCheck
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission de stockage accordée, lancer le sélecteur
                launchMediaStorePicker()
            }

            shouldShowRequestPermissionRationale(permissionToCheck) -> {
                // Afficher une explication
                showPermissionExplanationDialog(permissionToCheck)
            }

            else -> {
                // Demander la permission directement
                storagePermissionRequest.launch(permissionToCheck)
            }
        }
    }

    private fun launchMediaStorePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.type = "image/*"
        pickImagesLauncher.launch(intent)
    }

    private fun observeUploadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            uploadViewModel.uploadState.collectLatest { state ->
                updateUI(state)
                handleNotifications(state)
            }
        }
    }

    @androidx.annotation.RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleNotifications(state: UploadState) {
        // Vérifier la permission seulement si nécessaire (Android 13+)
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "UploadFragment",
                "Notification permission not granted on Android 13+, cannot show notifications."
            )
            return
        }

        // Mettre à jour la notification en utilisant UploadNotificationService
        notificationService.updateNotification(state)
    }

    private fun updateUI(state: UploadState?) {
        if (state == null) {
            // État initial ou inconnu, configurer une UI par défaut
            binding.progressUpload.visibility = View.GONE
            binding.tvUploadProgress.visibility = View.GONE
            binding.btnCancelUpload.visibility = View.GONE
            binding.btnRetryUpload.visibility = View.GONE
            binding.tvUploadStatus.text = getString(R.string.ready_to_upload)
            binding.btnUploadImages.isEnabled = travelId != -1L
            return
        }

        with(binding) {
            // Mettre à jour la barre de progression et le texte
            progressUpload.progress = state.progress
            tvUploadProgress.text = "${state.progressText} (${state.progress}%)"

            // Mettre à jour le texte de statut en fonction de l'état
            tvUploadStatus.text = when {
                state.isUploading -> getString(R.string.uploading_in_progress)
                state.isComplete && !state.hasErrors -> getString(R.string.upload_completed)
                state.hasErrors -> getString(R.string.upload_failed_count, state.failedUris.size)
                state.error != null && state.error == "Upload annulé par l'utilisateur" -> getString(
                    R.string.upload_cancelled_user
                )

                state.error != null -> getString(R.string.upload_failed_generic)
                else -> if (travelId != -1L) getString(R.string.ready_to_upload) else getString(R.string.select_travel_first)
            }

            // Gérer la visibilité des boutons et contrôles
            val showProgress = state.isUploading
            progressUpload.visibility = if (showProgress) View.VISIBLE else View.GONE
            tvUploadProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
            btnCancelUpload.visibility = if (showProgress) View.VISIBLE else View.GONE
            // Afficher Retry seulement s'il y a des erreurs et qu'aucun upload n'est en cours
            btnRetryUpload.visibility =
                if (state.hasErrors && !state.isUploading) View.VISIBLE else View.GONE
            // Désactiver le bouton Upload si un upload est en cours ou si aucun voyage n'est sélectionné
            btnUploadImages.isEnabled = !state.isUploading && travelId != -1L
        }
    }

    private fun startImageUpload(uris: List<Uri>) {
        if (uris.isEmpty()) return

        // Utiliser le ViewModel pour démarrer l'upload
        if (travelId != -1L) {
            uploadViewModel.uploadImages(uris, travelId)
        } else {
            binding.tvUploadStatus.text = getString(R.string.error_starting_upload_no_travel)
            Log.e("UploadFragment", "Tentative d'upload sans travelId valide.")
        }
    }

    // Fonction générique pour afficher un dialogue d'explication pour les permissions
    private fun showPermissionExplanationDialog(permission: String) {
        val explanationMessage = when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> getString(R.string.notification_permission_explanation)
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES ->
                getString(R.string.storage_permission_explanation)

            else -> getString(R.string.permission_needed_explanation_generic)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.permission_needed_title))
            .setMessage(explanationMessage)
            .setPositiveButton(getString(R.string.retry)) { _, _ ->
                when (permission) {
                    Manifest.permission.POST_NOTIFICATIONS ->
                        notificationPermissionRequest.launch(permission)

                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES ->
                        storagePermissionRequest.launch(permission)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPermissionSettingsDialog(explanationMessage: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.permission_denied_title))
            .setMessage(explanationMessage)
            .setPositiveButton(getString(R.string.settings)) { _, _ ->
                // Ouvrir les paramètres de l'application
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Éviter les fuites de mémoire
    }
}