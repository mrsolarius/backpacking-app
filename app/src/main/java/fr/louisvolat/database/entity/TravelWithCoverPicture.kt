package fr.louisvolat.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Classe de relation qui combine un Travel avec sa Picture de couverture
 */
data class TravelWithCoverPicture(
    @Embedded val travel: Travel,

    @Relation(
        parentColumn = "coverPictureId",
        entityColumn = "id"
    )
    val coverPicture: Picture?
)