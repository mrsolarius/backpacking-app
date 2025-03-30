package fr.louisvolat.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import fr.louisvolat.database.dao.CoordinateDao
import fr.louisvolat.database.dao.PictureDao
import fr.louisvolat.database.dao.TravelDao
import fr.louisvolat.database.entity.Coordinate
import fr.louisvolat.database.entity.Picture
import fr.louisvolat.database.entity.Travel

@Database(
    entities = [
        Coordinate::class,
        Travel::class,
        Picture::class,
    ],
    version = 2, // Augmenter la version
    exportSchema = false
)
@TypeConverters(Converter::class)
abstract class BackpakingLocalDataBase : RoomDatabase() {
    abstract fun travelDao(): TravelDao
    abstract fun pictureDao(): PictureDao
    abstract fun coordinateDao(): CoordinateDao

    companion object {
        @Volatile
        private var Instance: BackpakingLocalDataBase? = null

        fun getDatabase(context: Context): BackpakingLocalDataBase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    BackpakingLocalDataBase::class.java,
                    "backpacking_db" // Nom plus générique
                )
                    .fallbackToDestructiveMigration() // Pour la démo
                    .build()
                    .also { Instance = it }
            }
        }
    }
}