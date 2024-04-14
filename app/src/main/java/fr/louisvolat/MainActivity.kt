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
    private var tracking: Boolean = false;
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

    private fun handleTackButtonClick() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (!tracking) {
            this.buttonTrack.setText(R.string.start_track)
            if (checkLocationPermission()) {
                Toast.makeText(this, R.string.track_activated, Toast.LENGTH_SHORT).show()
                startForegroundService(Intent(this, LocationService::class.java))
                tracking = true
            } else {
                requestLocationPermission()
            }
        } else {
            this.buttonTrack.setText(R.string.stop_track)
            Toast.makeText(this, R.string.track_disable, Toast.LENGTH_SHORT).show()
            stopService(serviceIntent)
            tracking = false
        }
    }
}