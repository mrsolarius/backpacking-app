package fr.louisvolat.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Coordinate (
    @PrimaryKey val date: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
)