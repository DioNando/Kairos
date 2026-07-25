package com.kairos.app.data.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kairos.app.data.model.Task
import java.util.concurrent.TimeUnit

/**
 * Planifie (ou annule) un rappel par tâche via WorkManager. Chaque tâche a un travail **unique**
 * `reminder-<id>` : on le (re)programme si la tâche est active, a une échéance et que celle-ci
 * (moins le délai d'anticipation) est encore dans le futur ; sinon on l'annule.
 *
 * Appelé à chaque changement de la liste des tâches ou des réglages de rappel → l'état WorkManager
 * reste toujours synchrone avec les données. Une tâche supprimée disparaît de la liste : son travail
 * n'est pas annulé ici, mais le [ReminderWorker] relit la base et ignore un rappel devenu obsolète.
 */
object ReminderScheduler {

    private const val WORK_PREFIX = "reminder-"

    fun sync(context: Context, tasks: List<Task>, enabled: Boolean, leadMinutes: Int, now: Long) {
        val wm = WorkManager.getInstance(context)
        val leadMs = leadMinutes.toLong() * 60_000L
        tasks.forEach { task ->
            val work = WORK_PREFIX + task.id
            val due = task.dueAt
            val triggerAt = if (due != null) due - leadMs else null
            val shouldSchedule = enabled && task.isActive && triggerAt != null && triggerAt > now
            if (shouldSchedule) {
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(triggerAt!! - now, TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putLong(ReminderWorker.KEY_TASK_ID, task.id).build())
                    .addTag(WORK_PREFIX)
                    .build()
                // REPLACE : reprogramme proprement si l'échéance a changé.
                wm.enqueueUniqueWork(work, ExistingWorkPolicy.REPLACE, request)
            } else {
                wm.cancelUniqueWork(work)
            }
        }
    }

    /** Annule tous les rappels (rappels désactivés dans les réglages). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_PREFIX)
    }
}
