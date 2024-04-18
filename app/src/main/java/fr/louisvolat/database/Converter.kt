package fr.louisvolat.database

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


class Converter {
    companion object {
        fun fromTimestamp(value: Long?): LocalDateTime? {
            return value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }
        }

        fun dateToTimestamp(date: LocalDateTime?): Long? {
            return date?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }

        fun fromTimestampToString(value: Long?): String? {
            val formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
            val localDate = fromTimestamp(value)
            return localDate?.atOffset(ZoneOffset.UTC)?.format(formatter)
        }
    }
}