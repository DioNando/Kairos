package com.kairos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kairos.app.data.model.Attachment
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId ORDER BY createdAt ASC")
    suspend fun forTask(taskId: Long): List<Attachment>

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
