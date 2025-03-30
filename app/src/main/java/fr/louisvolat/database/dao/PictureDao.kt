package fr.louisvolat.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.louisvolat.database.entity.Picture

@Dao
interface PictureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(travels: List<Picture>)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(picture: Picture)

    @Query("SELECT * FROM pictures WHERE travelId = :travelId")
    fun getByTravel(travelId: Long): List<Picture>

    @Query("SELECT * FROM pictures WHERE id = :pictureId")
    suspend fun getPictureById(pictureId: Long): Picture?
}