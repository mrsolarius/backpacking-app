package fr.louisvolat.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import fr.louisvolat.R
import fr.louisvolat.databinding.FragmentMainBinding
import fr.louisvolat.locations.LocationService
import fr.louisvolat.worker.UploadLocationsWorker
import java.util.concurrent.TimeUnit

class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var buttonTrack: Button
    private lateinit var buttonSend: Button
    private var tracking = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment

        _binding = FragmentMainBinding.inflate(inflater, container, false)

        this.buttonTrack = binding.buttonTack
        this.buttonSend = binding.buttonSendData
        this.buttonTrack.setOnClickListener { this.handleTackButtonClick() }
        this.buttonSend.setOnClickListener { this.handleSendButtonClick() }
        return binding.root
    }

    private fun handleSendButtonClick() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkerRequest = OneTimeWorkRequest.Builder(UploadLocationsWorker::class.java)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(uploadWorkerRequest)
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            10
        )
    }

    private fun checkNotificationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS
            ),
            10
        )
    }

    private fun handleTackButtonClick() {
        if (!tracking) {
            this.buttonTrack.setText(R.string.stop_track)
            if (!checkNotificationPermission()) {
                requestNotificationPermission()
                return
            }
            if (checkLocationPermission()) {
                Toast.makeText(requireContext(), R.string.track_activated, Toast.LENGTH_SHORT).show()
                Intent(requireContext(), LocationService::class.java).also { intent ->
                    intent.action = LocationService.Action.START.toString()
                    requireActivity().startService(intent)
                }
                tracking = true
            } else {
                requestLocationPermission()
            }
        } else {
            this.buttonTrack.setText(R.string.start_track)
            Toast.makeText(requireContext(), R.string.track_disable, Toast.LENGTH_SHORT).show()
            Intent(requireContext(), LocationService::class.java).also { intent ->
                intent.action = LocationService.Action.STOP.toString()
                requireActivity().stopService(intent)
            }
            tracking = false
        }
    }
}