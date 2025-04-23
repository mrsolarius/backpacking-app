package fr.louisvolat.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Entity
data class Coordinate(
    @PrimaryKey val date: Long, // Timestamp UTC
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timeZoneId: String, // Identifiant du fuseau horaire (ex: "Europe/Paris")
    val timeZoneOffsetSeconds: Int, // Décalage en secondes par rapport à UTC
    val travelId: Long // ID du voyage associé
) {
    // Obtenir un ZonedDateTime avec le fuseau horaire original
    fun toZonedDateTime(): ZonedDateTime {
        val instant = Instant.ofEpochMilli(date)
        return instant.atZone(ZoneId.of(timeZoneId))
    }

    // Obtenir un String indiquant le décalage horaire au format GMT+X
    fun getTimeZoneOffsetString(): String {
        val offsetHours = timeZoneOffsetSeconds / 3600
        val sign = if (offsetHours >= 0) "+" else "-"
        return "GMT$sign${Math.abs(offsetHours)}"
    }

    companion object {
        // Créer une coordonnée avec le fuseau horaire actuel
        fun createWithCurrentTimeZone(
            timestamp: Long,
            latitude: Double,
            longitude: Double,
            altitude: Double,
            travelId: Long
        ): Coordinate {
            val zoneId = ZoneId.systemDefault()
            val instant = Instant.ofEpochMilli(timestamp)
            val zdt = instant.atZone(zoneId)
            val offsetSeconds = zdt.offset.totalSeconds

            return Coordinate(
                date = timestamp,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                timeZoneId = zoneId.id,
                timeZoneOffsetSeconds = offsetSeconds,
                travelId = travelId
            )
        }
    }
}