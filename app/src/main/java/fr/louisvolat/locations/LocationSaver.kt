package fr.louisvolat.locations

interface LocationSaver {
    fun saveLocation(latitude: Double, longitude: Double, altitude: Double, time: Long)
}