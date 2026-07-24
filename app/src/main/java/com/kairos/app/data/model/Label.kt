package com.kairos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Étiquette (catégorie) transversale : regroupe des tâches quel que soit leur quadrant
 * (Perso, Boulot, Maison…). [colorHex] est un « #RRGGBB » pour la pastille.
 */
@Entity(tableName = "labels")
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val createdAt: Long = 0,
)
