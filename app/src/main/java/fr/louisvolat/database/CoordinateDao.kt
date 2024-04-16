package fr.louisvolat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.LocalDateTime

@Dao
interface CoordinateDao {
    @Insert
    fun insert(coordinate: Coordinate)

    @Query("SELECT * FROM Coordinate")
    fun getAll(): List<Coordinate>

    @Query("SELECT * FROM Coordinate WHERE date >= :date")
    fun getFromDate(date: LocalDateTime): List<Coordinate>

    @Query("DELETE FROM Coordinate WHERE date < :date")
    fun deleteBeforeDate(date: LocalDateTime)
}