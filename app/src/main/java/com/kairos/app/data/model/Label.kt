package com.kairos.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Étiquette (catégorie) transversale : regroupe des tâches quel que soit leur quadrant
 * (Perso, Boulot, Maison…). [colorHex] est un « #RRGGBB » pour la pastille.
 *
 * [groupName] range l'étiquette dans un groupe (vide = sans groupe) ; une étiquette [pinned] passe en
 * tête des sélecteurs et sert de filtre rapide directement sur la matrice.
 */
@Entity(tableName = "labels")
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "''") val groupName: String = "",
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
)
