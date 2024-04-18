package fr.louisvolat.locations
import android.content.Context
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocationRequester (
    private var context: Context,
    private var timeInterval: Long,
    private var minimalDistance: Float,
    private var locationCallback: LocationSaver
) : LocationCallback() {

    private var request: LocationRequest
    private var locationClient: FusedLocationProviderClient

    init {
        // getting the location client
        locationClient = LocationServices.getFusedLocationProviderClient(context)
        request = createRequest()
    }

    private fun createRequest(): LocationRequest =
        // New builder
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, timeInterval).apply {
            setMinUpdateDistanceMeters(minimalDistance)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    fun startLocationTracking() {
        locationClient.requestLocationUpdates(request, this, Looper.getMainLooper())
    }


    fun stopLocationTracking() {
        locationClient.flushLocations()
        locationClient.removeLocationUpdates(this)
    }

    override fun onLocationResult(location: LocationResult) {
        //Display the location and the time in a Toast and in the log
        val locationData = location.lastLocation
        if (locationData == null) {
            Log.d("Location", "Location is null")
            Toast.makeText(this.context, "Location is null", Toast.LENGTH_SHORT).show()
            return
        }

        locationCallback.saveLocation(locationData.latitude,locationData.longitude,locationData.altitude,locationData.time)

        // Display the location in the log and in a Toast and don't forget time and date
        //get current date
        val currentDateTime = LocalDateTime.now()
        //convert current date to string with format "yyyy-MM-dd HH:mm:ss"
        val currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))

        Log.d("Location", "Location: ${locationData.latitude}, ${locationData.longitude}, Altitude: ${locationData.altitude} ;Time:${currentDateTimeString}; ")
        //Toast.makeText(this.context, "Time:${currentDateTimeString}\nLocation: ${location.latitude}, ${location.longitude}", Toast.LENGTH_LONG).show()
    }

    override fun onLocationAvailability(availability: LocationAvailability) {
        // TODO: react on the availability change
    }

}