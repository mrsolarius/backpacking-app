package fr.louisvolat.view

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fr.louisvolat.R
import fr.louisvolat.databinding.FragmentTrackerBinding
import fr.louisvolat.locations.LocationService
import fr.louisvolat.locations.SharedTrackingManager
import fr.louisvolat.locations.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TrackerFragment : Fragment() {
    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!
    private lateinit var buttonTrack: Button
    private lateinit var buttonSend: Button
    private lateinit var buttonStop: Button
    private var tracking = false

//    private val viewModel: LocationTrackingViewModel by viewModels {
//        val database = BackpakingLocalDataBase.getDatabase(requireContext())
//        val coordinateRepository = CoordinateRepository(
//            database.coordinateDao(),
//            ApiClient.getInstance(requireContext()),
//            requireContext()
//        )
//        LocationTrackingViewModelFactory(
//            coordinateRepository,
//            requireActivity().application
//        )
//    }

    // Les récepteurs sont toujours nécessaires pour certains événements spécifiques
    private var trackingUpdateReceiver: BroadcastReceiver? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        _binding = FragmentTrackerBinding.inflate(inflater, container, false)

        // Initialiser les boutons
        buttonTrack = binding.buttonTrack
        buttonStop = binding.buttonStop
        buttonSend = binding.buttonSendData

        // Configurer les listeners initiaux
        buttonTrack.setOnClickListener { startTracking() }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observer les changements d'état via le SharedTrackingManager
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SharedTrackingManager.trackingUpdates.collect { state ->
                    updateUI(state)
                }
            }
        }



        // Enregistrer le récepteur uniquement pour les événements non disponibles via le SharedTrackingManager
        registerTrackingReceiver()
    }

    private fun updateUI(state: TrackingState) {
        // Mise à jour de l'interface utilisateur
        binding.apply {
            textTrackingTime.text = state.getFormattedDuration()

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

                // Mettre à jour le flag de tracking
                tracking = true
            } else {
                buttonTrack.text = getString(R.string.start_track)
                buttonTrack.setOnClickListener { startTracking() }
                buttonStop.visibility = View.GONE
                statusIndicator.setImageResource(R.drawable.outlined_stop_24)
                statusText.text = getString(R.string.tracking_stopped)

                // Mettre à jour le flag de tracking
                tracking = false
            }
        }
    }

    private fun registerTrackingReceiver() {
        // Créer et enregistrer le récepteur pour les mises à jour spécifiques
        trackingUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Gérer uniquement les événements qui ne sont pas déjà traités par SharedTrackingManager
                if (intent.action == LocationService.ACTION_TRACKING_TIME_UPDATE) {
                    // Cette partie peut être supprimée si le SharedTrackingManager gère déjà correctement les mises à jour de temps
                    val duration = intent.getLongExtra(LocationService.EXTRA_TRACKING_DURATION, 0L)
                    binding.textTrackingTime.text = formatDuration(duration)
                }
            }
        }

        // Enregistrer le récepteur
        ContextCompat.registerReceiver(
            requireContext(),
            trackingUpdateReceiver,
            IntentFilter(LocationService.ACTION_TRACKING_TIME_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterReceiver() {
        trackingUpdateReceiver?.let {
            requireContext().unregisterReceiver(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterReceiver()
        _binding = null
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun startTracking() {
        // Récupérer l'ID du voyage depuis les arguments
        val travelId = arguments?.getLong("travelId") ?: -1L
        if (travelId == -1L) {
            Toast.makeText(
                requireContext(),
                "Impossible de démarrer le tracking: aucun voyage sélectionné",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!checkPermissions()) return

        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.START.name
            intent.putExtra(LocationService.EXTRA_TRAVEL_ID, travelId)
            requireContext().startForegroundService(intent)
        }
    }

    private fun pauseTracking() {
        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.PAUSE.name
            requireContext().startService(intent)
        }
    }

    private fun resumeTracking() {
        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.RESUME.name
            requireContext().startService(intent)
        }
    }

    private fun stopTracking() {
        Intent(requireContext(), LocationService::class.java).also { intent ->
            intent.action = LocationService.Action.STOP.name
            requireContext().startService(intent)
        }
    }

    private fun checkPermissions(): Boolean {
        // Vérifier les permissions de localisation et de notification
        val hasLocationPermission = (
                ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                )

        val hasNotificationPermission =
            ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        // Demander les permissions manquantes
        val permissionsToRequest = mutableListOf<String>()

        if (!hasLocationPermission) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (!hasNotificationPermission) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(
                permissionsToRequest.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
            return false
        }

        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            // Vérifier si toutes les permissions ont été accordées
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                startTracking()
            } else {
                // Si certaines permissions ont été refusées, afficher un message
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // L'utilisateur a refusé mais n'a pas coché "Ne plus demander"
                    showPermissionExplanationDialog()
                } else {
                    // L'utilisateur a coché "Ne plus demander"
                    showPermissionSettingsDialog()
                }
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permissions nécessaires")
            .setMessage("Cette fonctionnalité nécessite les permissions de localisation et de notification pour fonctionner correctement.")
            .setPositiveButton("Réessayer") { _, _ -> checkPermissions() }
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

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }

    override fun onStart() {
        super.onStart()
        Log.d("Fragment", "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Fragment", "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d("Fragment", "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Fragment", "onStop")
    }
}