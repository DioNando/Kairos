package com.kairos.app.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kairos.app.data.local.KairosDatabase

/**
 * Affiche le rappel d'une tâche à l'heure planifiée. Relit la tâche en base au moment du
 * déclenchement : si elle a été terminée, supprimée ou son échéance retirée entre-temps, le rappel
 * est simplement ignoré (pas de notification obsolète).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.success()

        val task = KairosDatabase.getInstance(applicationContext).taskDao().getById(taskId)
            ?: return Result.success()
        if (!task.isActive || task.dueAt == null) return Result.success()

        ReminderNotifications.show(applicationContext, task.id, task.title, task.note)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
