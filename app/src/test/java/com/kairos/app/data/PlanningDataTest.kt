package com.kairos.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kairos.app.data.local.KairosDatabase
import com.kairos.app.data.model.Task
import com.kairos.app.data.model.TimeBlock
import com.kairos.app.data.repository.AttachmentStore
import com.kairos.app.data.repository.KairosRepository
import com.kairos.app.data.repository.SettingsRepository
import com.kairos.app.domain.backup.BackupManager
import com.kairos.app.domain.recurrence.Recurrence
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Règles du planning qui touchent aux données : fin de session, récurrence, sauvegarde. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlanningDataTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private lateinit var db: KairosDatabase
    private lateinit var repository: KairosRepository
    private lateinit var backup: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KairosDatabase::class.java).allowMainThreadQueries().build()
        repository = KairosRepository(
            taskDao = db.taskDao(),
            labelDao = db.labelDao(),
            subtaskDao = db.subtaskDao(),
            savedFilterDao = db.savedFilterDao(),
            attachmentDao = db.attachmentDao(),
            attachmentStore = AttachmentStore(context),
            timeBlockDao = db.timeBlockDao(),
        )
        backup = BackupManager(
            taskDao = db.taskDao(),
            labelDao = db.labelDao(),
            subtaskDao = db.subtaskDao(),
            savedFilterDao = db.savedFilterDao(),
            timeBlockDao = db.timeBlockDao(),
            settingsRepository = SettingsRepository(context),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun task(estimate: Int = 0, recurrence: Recurrence = Recurrence.NONE, dueAt: Long? = null): Long =
        repository.saveTask(
            Task(title = "t", estimateMinutes = estimate, recurrence = recurrence.ordinal, dueAt = dueAt),
            now,
        )

    @Test
    fun completingTheOnlySession_completesTheTask() = runBlocking {
        val id = task()
        val block = repository.addBlock(id, now, 30)
        repository.completeBlock(block, now)
        assertEquals(now, db.taskDao().getById(id)?.completedAt)
    }

    @Test
    fun completingOneOfSeveralSessions_keepsTheTaskActive() = runBlocking {
        val id = task()
        val first = repository.addBlock(id, now, 30)
        val second = repository.addBlock(id, now + hour, 30)
        repository.completeBlock(first, now)
        assertNull(db.taskDao().getById(id)?.completedAt)
        repository.completeBlock(second, now)
        assertEquals(now, db.taskDao().getById(id)?.completedAt)
    }

    @Test
    fun completingTheLastSession_keepsTheTaskActiveWhileTheEstimateIsNotCovered() = runBlocking {
        val id = task(estimate = 120)
        repository.completeBlock(repository.addBlock(id, now, 60), now)
        assertNull(db.taskDao().getById(id)?.completedAt)
        repository.completeBlock(repository.addBlock(id, now + hour, 60), now)
        assertEquals(now, db.taskDao().getById(id)?.completedAt)
    }

    @Test
    fun recurringTask_advancesAndGoesBackToUnscheduled() = runBlocking {
        val id = task(recurrence = Recurrence.DAILY, dueAt = now + hour)
        repository.completeBlock(repository.addBlock(id, now, 30), now)
        val updated = db.taskDao().getById(id)
        assertNull(updated?.completedAt)
        assertTrue(updated?.dueAt!! > now + hour)
        assertTrue(db.timeBlockDao().forTask(id).isEmpty())
    }

    @Test
    fun clearBlocks_keepsCompletedSessions() = runBlocking {
        val id = task()
        repository.addBlock(id, now, 30)
        db.timeBlockDao().insert(TimeBlock(taskId = id, start = now - hour, minutes = 30, doneAt = now))
        repository.clearBlocks(id)
        assertEquals(listOf(now), db.timeBlockDao().forTask(id).map { it.doneAt })
    }

    @Test
    fun deletingATask_deletesItsSessions() = runBlocking {
        val id = task()
        repository.addBlock(id, now, 30)
        repository.deleteTask(id)
        assertTrue(db.timeBlockDao().getAll().isEmpty())
    }

    @Test
    fun backup_roundTripKeepsSessionsAndEstimate() = runBlocking {
        val id = task(estimate = 90)
        repository.addBlock(id, now, 30)
        repository.addBlock(id, now + hour, 60)
        val json = backup.export()
        db.taskDao().clear()
        backup.import(json)
        assertEquals(90, db.taskDao().getById(id)?.estimateMinutes)
        assertEquals(listOf(now, now + hour), db.timeBlockDao().getAll().map { it.start })
    }

    @Test
    fun oldBackup_singleSlotBecomesASession() = runBlocking {
        val json = """
            {"version": 3, "tasks": [
              {"id": 1, "title": "planifiée", "blockStart": $now, "blockMinutes": 45},
              {"id": 2, "title": "durée par défaut", "blockStart": $now, "blockMinutes": 0},
              {"id": 3, "title": "terminée", "blockStart": $now, "completedAt": ${now + hour}},
              {"id": 4, "title": "sans créneau"}
            ]}
        """.trimIndent()
        backup.import(json)
        val blocks = db.timeBlockDao().getAll().associateBy { it.taskId }
        assertEquals(setOf(1L, 2L, 3L), blocks.keys)
        assertEquals(45, blocks.getValue(1).minutes)
        assertEquals(Task.DEFAULT_BLOCK_MINUTES, blocks.getValue(2).minutes)
        assertEquals(now + hour, blocks.getValue(3).doneAt)
    }
}
