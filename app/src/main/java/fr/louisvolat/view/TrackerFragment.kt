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
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fr.louisvolat.R
import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.repository.PictureRepository
import fr.louisvolat.data.repository.TrackingRepositoryProvider
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.data.viewmodel.TrackingViewModel
import fr.louisvolat.data.viewmodel.TrackingViewModelFactory
import fr.louisvolat.data.viewmodel.TravelViewModel
import fr.louisvolat.data.viewmodel.TravelViewModelFactory
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.databinding.FragmentTrackerBinding
import fr.louisvolat.locations.LocationService
import fr.louisvolat.locations.TrackingState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TrackerFragment : Fragment() {
    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!
    private lateinit var buttonTrack: Button
    private lateinit var buttonSend: Button
    private lateinit var buttonStop: Button

    private lateinit var travelViewModel: TravelViewModel
    private var travelId: Long = -1

    // Référence au ViewModel
    private lateinit var trackingViewModel: TrackingViewModel

    // Lanceurs pour les permissions
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionRequest: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialiser les lanceurs pour les permissions
        registerPermissionLaunchers()
    }

    private fun registerPermissionLaunchers() {
        // Lanceur pour les permissions de localisation
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                // Vérifier si la permission de notification est nécessaire
                checkNotificationPermission()
            } else {
                // Les permissions de localisation ont été refusées
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    showPermissionExplanationDialog()
                } else {
                    showPermissionSettingsDialog()
                }
            }
        }

        // Lanceur pour la permission de notification
        notificationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Toutes les permissions sont accordées, démarrer le tracking
                proceedWithTracking()
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackerBinding.inflate(inflater, container, false)

        // Initialiser le ViewModel de voyage
        val database = BackpakingLocalDataBase.getDatabase(requireContext())
        val pictureRepository = PictureRepository(
            database.pictureDao(),
            ApiClient.getInstance(requireContext()),
        )
        travelViewModel = ViewModelProvider(
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

        // Initialiser les boutons
        buttonTrack = binding.buttonTrack
        buttonStop = binding.buttonStop
        buttonSend = binding.buttonSendData

        // Utiliser l'instance partagée du repository
        val trackingRepository = TrackingRepositoryProvider.getInstance(requireContext())
        val factory = TrackingViewModelFactory(trackingRepository)
        trackingViewModel = ViewModelProvider(this, factory)[TrackingViewModel::class.java]

        travelViewModel.selectedTravelId.observe(viewLifecycleOwner) { id ->
            travelId = id
            Log.d("TrackerFragment", "TravelId mis à jour: $travelId")
        }

        // Configurer les listeners initiaux
        buttonTrack.setOnClickListener { startTracking() }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()

        // Observer les erreurs
        trackingViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        // IMPORTANT: Utiliser repeatOnLifecycle pour garantir la collecte appropriée
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Collecter les changements d'état généraux pour mettre à jour l'UI
                launch {
                    trackingViewModel.trackingState.collectLatest { state ->
                        Log.d("TrackerFragment", "État général mis à jour: ${state.isTracking}, ${state.isPaused}")
                        updateUI(state)
                    }
                }

                // 2. Collecter spécifiquement les mises à jour du timer pour le temps écoulé
                // Utiliser collectLatest garantit que nous recevons toujours la dernière valeur
                launch {
                    trackingViewModel.timerUpdates.collectLatest { state ->
                        Log.d("TrackerFragment", "Mise à jour timer: ${state.getFormattedDuration()}")
                        if (state.isTracking) {
                            binding.textTrackingTime.text = state.getFormattedDuration()
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(state: TrackingState) {
        // Mise à jour de l'interface utilisateur
        binding.apply {
            // Remarque: Le temps est mis à jour séparément par la collecte de timerUpdates

            if (state.isTracking) {
                buttonTrack.text = if (state.isPaused)
                    getString(R.string.resume_tracking)
                else
                    getString(R.string.pause_tracking)

                // Mise à jour des listeners
                buttonTrack.setOnClickListener {
                    if (state.isPaused) resumeTracking() else pauseTracking()
                }

                buttonStop.visibility = View.VISIBLE
                buttonStop.setOnClickListener { stopTracking() }

                statusIndicator.setImageResource(
                    if (state.isPaused) R.drawable.outlined_pause_24 else R.drawable.outlined_play_arrow_24
                )
                statusText.text = if (state.isPaused)
                    getString(R.string.tracking_paused)
                else
                    getString(R.string.tracking_running)
            } else {
                buttonTrack.text = getString(R.string.start_track)
                buttonTrack.setOnClickListener { startTracking() }
                buttonStop.visibility = View.GONE
                statusIndicator.setImageResource(R.drawable.outlined_stop_24)
                statusText.text = getString(R.string.tracking_stopped)

                // Réinitialiser le chronomètre si le tracking est arrêté
                textTrackingTime.text = getString(R.string.defaultTimer)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startTracking() {
        // Récupérer l'ID du voyage depuis les arguments
        if (travelId == -1L) {
            Toast.makeText(
                requireContext(),
                "Impossible de démarrer le tracking: aucun voyage sélectionné",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Vérifier les permissions
        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        // Vérifier les permissions de localisation
        val hasFineLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            // Permissions de localisation accordées, vérifier la permission de notification
            checkNotificationPermission()
        } else {
            // Demander les permissions de localisation
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkNotificationPermission() {
        // La permission de notification est seulement nécessaire à partir d'Android 13 (API 33)
        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasNotificationPermission) {
            // Toutes les permissions nécessaires sont accordées
            proceedWithTracking()
        } else {
            // Demander la permission de notification
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun proceedWithTracking() {
        Log.d("TrackerFragment", "Démarrage du tracking pour le voyage $travelId")

        // D'abord démarrer le service
        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.START.name
            intent.putExtra(LocationService.EXTRA_TRAVEL_ID, travelId)
            requireContext().startForegroundService(intent)
        }

        // Ensuite utiliser le ViewModel pour démarrer le tracking via la logique métier
        trackingViewModel.startTracking(travelId)
    }

    private fun pauseTracking() {
        Log.d("TrackerFragment", "Mise en pause du tracking")

        // Utiliser le ViewModel pour mettre en pause le tracking
        trackingViewModel.pauseTracking()

        // Puis envoyer la commande au service
        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.PAUSE.name
            requireContext().startService(intent)
        }
    }

    private fun resumeTracking() {
        Log.d("TrackerFragment", "Reprise du tracking")

        // Utiliser le ViewModel pour reprendre le tracking
        trackingViewModel.resumeTracking()

        // Puis envoyer la commande au service
        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.RESUME.name
            requireContext().startService(intent)
        }
    }

    private fun stopTracking() {
        Log.d("TrackerFragment", "Arrêt du tracking")

        // D'abord envoyer la commande d'arrêt au service
        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.STOP.name
            requireContext().startService(intent)
        }

        // Puis utiliser le ViewModel pour mettre à jour l'état
        trackingViewModel.stopTracking()
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permissions nécessaires")
            .setMessage("Cette fonctionnalité nécessite les permissions de localisation et de notification pour fonctionner correctement.")
            .setPositiveButton("Réessayer") { _, _ -> checkLocationPermissions() }
            .setNegativeButton("Annuler") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permissions refusées")
            .setMessage("Les permissions nécessaires ont été refusées définitivement. Veuillez les activer dans les paramètres de l'application.")
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
}