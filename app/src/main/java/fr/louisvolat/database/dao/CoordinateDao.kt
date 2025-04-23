package fr.louisvolat.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.louisvolat.database.entity.Coordinate
import kotlinx.coroutines.flow.Flow

@Dao
interface CoordinateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(coordinate: Coordinate)

    @Query("SELECT * FROM Coordinate")
    fun getAll(): List<Coordinate>

    @Query("SELECT * FROM Coordinate WHERE date >= :date")
    fun getFromDate(date: Long): List<Coordinate>

    @Query("SELECT * FROM Coordinate WHERE travelId = :travelId")
    fun getByTravelId(travelId: Long): List<Coordinate>

    @Query("SELECT * FROM Coordinate WHERE travelId = :travelId")
    fun getByTravelIdFlow(travelId: Long): Flow<List<Coordinate>>

    @Query("SELECT * FROM Coordinate WHERE travelId = :travelId AND date >= :date")
    fun getByTravelIdFromDate(travelId: Long, date: Long): List<Coordinate>

    @Query("DELETE FROM Coordinate WHERE date < :date")
    fun deleteBeforeDate(date: Long)

    @Query("DELETE FROM Coordinate WHERE travelId = :travelId")
    fun deleteByTravelId(travelId: Long)

    @Query("SELECT COUNT(*) FROM Coordinate WHERE travelId = :travelId")
    fun getCountForTravel(travelId: Long): Int
}