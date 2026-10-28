package com.kairos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kairos.app.data.model.Label
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    /**
     * Ordre d'affichage : épinglées d'abord, puis sans groupe, puis par groupe ; dans chaque section,
     * l'ordre manuel ([Label.position]), puis la création.
     */
    @Query(
        "SELECT * FROM labels ORDER BY pinned DESC, (groupName = '') DESC, " +
            "groupName COLLATE NOCASE ASC, position ASC, createdAt ASC",
    )
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

    @Query("SELECT COALESCE(MAX(position), -1) FROM labels")
    suspend fun maxPosition(): Int

    @Query("UPDATE labels SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Réordonne d'un bloc : [ids] dans l'ordre voulu reçoivent les positions 0, 1, 2… */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setPosition(id, index) }
    }

    @Query("UPDATE labels SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM labels")
    suspend fun clear()
}
