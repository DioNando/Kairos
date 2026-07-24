package com.kairos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kairos.app.data.model.Label
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Query("SELECT * FROM labels ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Label>>

    @Query("SELECT * FROM labels ORDER BY id ASC")
    suspend fun getAll(): List<Label>

    @Query("SELECT COUNT(*) FROM labels")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: Label): Long

    @Update
    suspend fun update(label: Label)

    /** Supprime l'étiquette et détache les tâches qui la portaient (labelId -> NULL). */
    @Query("UPDATE tasks SET labelId = NULL WHERE labelId = :id")
    suspend fun detachTasks(id: Long)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM labels")
    suspend fun clear()
}
