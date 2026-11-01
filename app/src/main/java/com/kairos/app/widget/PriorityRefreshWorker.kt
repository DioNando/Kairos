package com.kairos.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kairos.app.KairosApp
import java.util.concurrent.TimeUnit

/**
 * Recalcule toutes les heures ce qui dépend du temps qui passe : sans modification de tâche, une
 * échéance qui approche change le classement, mais rien d'autre ne réveillerait le widget « À faire
 * en priorité » ni la montre.
 */
class PriorityRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        (applicationContext as? KairosApp)?.refreshPriorities()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "priority-refresh"

        /** KEEP : la période n'est pas remise à zéro à chaque lancement de l'app. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriorityRefreshWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
