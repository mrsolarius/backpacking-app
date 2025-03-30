package fr.louisvolat.database.entity

import androidx.room.*

@Entity(tableName = "travels")
data class Travel(
    @PrimaryKey
    val id: Long = 0,

    val name: String,
    val description: String,
    val startDate: Long, // Timestamp en millisecondes
    val endDate: Long? = null,
    val coverPictureId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis()
)