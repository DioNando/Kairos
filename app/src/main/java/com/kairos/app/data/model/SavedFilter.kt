package com.kairos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Filtre enregistré de la matrice : recherche texte ([query], titre/note) et/ou étiquette
 * ([labelId]). Réappliqué d'un tap depuis le panneau de recherche.
 */
@Entity(tableName = "saved_filters")
data class SavedFilter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val query: String = "",
    val labelId: Long? = null,
    val createdAt: Long = 0,
)
