package fr.louisvolat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import fr.louisvolat.databinding.ActivityMainBinding
import fr.louisvolat.services.LocationService


class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding
    private var tracking: Boolean = false
    private lateinit var buttonTrack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        this.buttonTrack = binding.buttonTack

        this.buttonTrack.setOnClickListener { this.handleTackButtonClick() }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            10
        )
    }

    private fun checkNotificationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        ActivityCompat.requestPermissions(
            this,
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
                Toast.makeText(this, R.string.track_activated, Toast.LENGTH_SHORT).show()
                Intent(this, LocationService::class.java).also { intent ->
                    intent.action = LocationService.Action.START.toString()
                    startService(intent)
                }
                tracking = true
            } else {
                requestLocationPermission()
            }
        } else {
            this.buttonTrack.setText(R.string.start_track)
            Toast.makeText(this, R.string.track_disable, Toast.LENGTH_SHORT).show()
            Intent(this, LocationService::class.java).also { intent ->
                intent.action = LocationService.Action.STOP.toString()
                stopService(intent)
            }
            tracking = false
        }
    }
}