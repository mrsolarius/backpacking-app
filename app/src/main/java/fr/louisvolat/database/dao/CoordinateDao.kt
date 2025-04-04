package fr.louisvolat.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import fr.louisvolat.database.entity.Coordinate

@Dao
interface CoordinateDao {
    @Insert
    fun insert(coordinate: Coordinate)

    @Query("SELECT * FROM Coordinate")
    fun getAll(): List<Coordinate>

    @Query("SELECT * FROM Coordinate WHERE date >= :date")
    fun getFromDate(date: Long): List<Coordinate>

    @Query("DELETE FROM Coordinate WHERE date < :date")
    fun deleteBeforeDate(date: Long)
}