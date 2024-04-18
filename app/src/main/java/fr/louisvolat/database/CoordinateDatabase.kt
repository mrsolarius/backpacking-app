package fr.louisvolat.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Coordinate::class], version = 1)
abstract class CoordinateDatabase : RoomDatabase() {
    abstract fun coordinateDao(): CoordinateDao

    companion object{
        @Volatile
        private var Instance: CoordinateDatabase? = null

        fun getDatabase(context: Context): CoordinateDatabase {
            return Instance ?: synchronized(this) {
                return Instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CoordinateDatabase::class.java,
                    "coordinate_database"
                ).build().also {
                    Instance = it
                }
            }
        }
    }
}