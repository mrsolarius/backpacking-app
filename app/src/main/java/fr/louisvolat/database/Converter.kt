package fr.louisvolat.database

import androidx.room.TypeConverter
import fr.louisvolat.database.entity.Coordinate
import java.time.*
import java.time.format.DateTimeFormatter

class Converter {
    companion object {
        // Convertir un timestamp en ZonedDateTime avec un fuseau horaire spécifique
        fun fromTimestampWithTimeZone(value: Long?, timeZoneId: String?, offsetSeconds: Int?): ZonedDateTime? {
            if (value == null) return null

            val instant = Instant.ofEpochMilli(value)

            // Si on a un ID de fuseau horaire, l'utiliser
            if (timeZoneId != null) {
                return try {
                    instant.atZone(ZoneId.of(timeZoneId))
                } catch (e: Exception) {
                    // Fallback sur l'offset si l'ID n'est pas valide
                    if (offsetSeconds != null) {
                        instant.atZone(ZoneOffset.ofTotalSeconds(offsetSeconds))
                    } else {
                        instant.atZone(ZoneId.systemDefault())
                    }
                }
            }
            // Sinon utiliser l'offset si disponible
            else if (offsetSeconds != null) {
                return instant.atZone(ZoneOffset.ofTotalSeconds(offsetSeconds))
            }

            // Fallback sur le fuseau horaire système
            return instant.atZone(ZoneId.systemDefault())
        }

        // Formater un timestamp avec son fuseau horaire
        fun fromTimestampToStringWithTimeZone(
            value: Long?,
            timeZoneId: String?,
            offsetSeconds: Int?
        ): String? {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            val zonedDateTime = fromTimestampWithTimeZone(value, timeZoneId, offsetSeconds)
            return zonedDateTime?.format(formatter)
        }

        // Obtenir le fuseau horaire actuel
        fun getCurrentTimeZoneId(): String {
            return ZoneId.systemDefault().id
        }

        // Obtenir l'offset actuel en secondes
        fun getCurrentTimeZoneOffsetSeconds(): Int {
            return ZonedDateTime.now().offset.totalSeconds
        }

        // Formater un offset en chaîne lisible (ex: GMT+2)
        fun formatOffsetToGMTString(offsetSeconds: Int): String {
            val hours = offsetSeconds / 3600
            val sign = if (hours >= 0) "+" else "-"
            return "GMT$sign${Math.abs(hours)}"
        }
        fun fromTimestampToZonedDateTime(coordinate: Coordinate): ZonedDateTime {
            val instant = Instant.ofEpochMilli(coordinate.date)

            // Si l'entité a un timeZoneId, l'utiliser
            return if (coordinate.timeZoneId.isNotEmpty()) {
                try {
                    instant.atZone(ZoneId.of(coordinate.timeZoneId))
                } catch (e: Exception) {
                    // Si le timeZoneId n'est pas valide, utiliser l'offset si disponible
                    if (coordinate.timeZoneOffsetSeconds != 0) {
                        instant.atZone(ZoneOffset.ofTotalSeconds(coordinate.timeZoneOffsetSeconds))
                    } else {
                        instant.atZone(ZoneId.systemDefault())
                    }
                }
            }
            // Sinon, utiliser l'offset si disponible
            else if (coordinate.timeZoneOffsetSeconds != 0) {
                instant.atZone(ZoneOffset.ofTotalSeconds(coordinate.timeZoneOffsetSeconds))
            }
            // Fallback sur le fuseau horaire système
            else {
                instant.atZone(ZoneId.systemDefault())
            }
        }
    }

    @TypeConverter
    fun fromZonedDateTime(date: ZonedDateTime?): Long? = date?.toInstant()?.toEpochMilli()

    @TypeConverter
    fun toZonedDateTime(millis: Long?): ZonedDateTime? = millis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
    }
}