package com.kairos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kairos.app.data.model.SavedFilter
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedFilterDao {

    @Query("SELECT * FROM saved_filters ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SavedFilter>>

    @Query("SELECT * FROM saved_filters ORDER BY id ASC")
    suspend fun getAll(): List<SavedFilter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: SavedFilter): Long

    @Query("DELETE FROM saved_filters WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Une étiquette supprimée ne doit plus être référencée par un filtre (il garde sa recherche). */
    @Query("UPDATE saved_filters SET labelId = NULL WHERE labelId = :labelId")
    suspend fun detachLabel(labelId: Long)

    @Query("DELETE FROM saved_filters")
    suspend fun clear()
}
