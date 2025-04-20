package fr.louisvolat.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import fr.louisvolat.database.entity.Travel
import fr.louisvolat.database.entity.TravelWithCoverPicture
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(travels: List<Travel>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(travel: Travel)

    @Update
    suspend fun update(travel: Travel)

    @Query("SELECT * FROM travels WHERE id = :id")
    suspend fun getById(id: Long): Travel?

    @Query("SELECT * FROM travels")
    fun getAll(): Flow<List<Travel>>

    @Transaction
    @Query("SELECT * FROM travels")
    fun getAllWithCoverPictures(): Flow<List<TravelWithCoverPicture>>
}