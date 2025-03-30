package fr.louisvolat.database.entity

import androidx.room.*

@Entity(
    tableName = "pictures",
    foreignKeys = [ForeignKey(
        entity = Travel::class,
        parentColumns = ["id"],
        childColumns = ["travelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("travelId")]
)
data class Picture(
    @PrimaryKey()
    val id: Long = 0,

    val latitude: String, // Coordonnées GPS directement dans l'image
    val longitude: String,
    val altitude: String? = null,

    val date: Long, // Utilisera votre Converter
    val rawVersion: String,
    val localPath: String?,

    val travelId: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis()
)