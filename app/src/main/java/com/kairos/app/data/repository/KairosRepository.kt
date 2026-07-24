package com.kairos.app.data.repository

import com.kairos.app.data.local.LabelDao
import com.kairos.app.data.local.SubtaskDao
import com.kairos.app.data.local.TaskDao
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task
import com.kairos.app.domain.recurrence.Recurrence
import kotlinx.coroutines.flow.Flow

/** Point d'accès unique aux données (tâches + étiquettes + sous-tâches) pour les ViewModels. */
class KairosRepository(
    private val taskDao: TaskDao,
    private val labelDao: LabelDao,
    private val subtaskDao: SubtaskDao,
) {
    val tasks: Flow<List<Task>> = taskDao.observeAll()
    val labels: Flow<List<Label>> = labelDao.observeAll()
    val subtasks: Flow<List<Subtask>> = subtaskDao.observeAll()

    suspend fun getTask(id: Long): Task? = taskDao.getById(id)

    /** Crée ou met à jour une tâche. En création, la place en fin de son quadrant. */
    suspend fun saveTask(task: Task, now: Long): Long {
        if (task.id == 0L) {
            val position = taskDao.maxPosition(task.important, task.urgent) + 1
            return taskDao.insert(task.copy(position = position, createdAt = now))
        }
        taskDao.update(task)
        return task.id
    }

    /**
     * Enregistre une tâche **et** remplace intégralement sa checklist. Les sous-tâches sont
     * réinsérées à neuf (ids régénérés) dans l'ordre fourni.
     */
    suspend fun saveTaskWithSubtasks(task: Task, subtasks: List<Subtask>, now: Long): Long {
        val id = saveTask(task, now)
        subtaskDao.deleteForTask(id)
        subtasks.forEachIndexed { index, sub ->
            subtaskDao.insert(sub.copy(id = 0, taskId = id, position = index))
        }
        return id
    }

    /** Sous-tâches d'une tâche (pour capturer avant suppression / annulation). */
    suspend fun getSubtasks(taskId: Long): List<Subtask> = subtaskDao.forTask(taskId)

    /**
     * Ré-insère une tâche supprimée en conservant son id (annulation d'une suppression), et
     * restaure ses sous-tâches (perdues par la cascade FK à la suppression).
     */
    suspend fun restoreTask(task: Task, subtasks: List<Subtask> = emptyList()) {
        taskDao.insert(task)
        subtasks.forEach { subtaskDao.insert(it) }
    }

    /** Déplace une tâche dans un quadrant (menu accessible « Déplacer vers… » ou glisser). */
    suspend fun moveTo(taskId: Long, quadrant: Quadrant) {
        val task = taskDao.getById(taskId) ?: return
        if (task.important == quadrant.important && task.urgent == quadrant.urgent) return
        val position = taskDao.maxPosition(quadrant.important, quadrant.urgent) + 1
        taskDao.update(task.copy(important = quadrant.important, urgent = quadrant.urgent, position = position))
    }

    /**
     * Réordonne une tâche **dans son propre quadrant** : l'insère à [targetIndex] parmi les tâches
     * actives du quadrant et renumérote les positions de façon dense.
     */
    suspend fun reorder(taskId: Long, targetIndex: Int) {
        val task = taskDao.getById(taskId) ?: return
        val active = taskDao.activeInQuadrant(task.important, task.urgent)
        val without = active.filter { it.id != taskId }
        val clamped = targetIndex.coerceIn(0, without.size)
        val ordered = without.toMutableList().apply { add(clamped, task) }
        ordered.forEachIndexed { index, t ->
            if (t.position != index) taskDao.updatePosition(t.id, index)
        }
    }

    /** Bascule urgent/important (accepter la suggestion d'échéance = passer urgent = true). */
    suspend fun setUrgent(taskId: Long, urgent: Boolean) {
        val task = taskDao.getById(taskId) ?: return
        if (task.urgent == urgent) return
        val position = taskDao.maxPosition(task.important, urgent) + 1
        taskDao.update(task.copy(urgent = urgent, position = position))
    }

    /**
     * Marque une tâche terminée ([completedAt] renseignée) ou active (null). À la **complétion**
     * d'une tâche récurrente, régénère une occurrence active : échéance avancée, temps de focus
     * remis à zéro, sous-tâches recopiées non cochées. L'occurrence terminée part à l'archive.
     *
     * @return l'id de l'occurrence régénérée (pour pouvoir l'annuler), ou `null` s'il n'y en a pas.
     */
    suspend fun setCompleted(taskId: Long, completedAt: Long?, now: Long): Long? {
        val task = taskDao.getById(taskId) ?: return null
        taskDao.update(task.copy(completedAt = completedAt))
        if (completedAt == null || task.recurrenceType == Recurrence.NONE) return null
        val nextDue = task.recurrenceType.next(task.dueAt ?: completedAt)
        val position = taskDao.maxPosition(task.important, task.urgent) + 1
        val nextId = taskDao.insert(
            task.copy(
                id = 0,
                dueAt = nextDue,
                completedAt = null,
                focusSeconds = 0,
                position = position,
                createdAt = now,
            ),
        )
        subtaskDao.forTask(task.id).forEachIndexed { index, sub ->
            subtaskDao.insert(sub.copy(id = 0, taskId = nextId, done = false, position = index))
        }
        return nextId
    }

    suspend fun deleteTask(taskId: Long) = taskDao.deleteById(taskId)

    /** Vide l'archive (toutes les tâches terminées). */
    suspend fun clearArchive() = taskDao.clearCompleted()

    /** Purge automatique : supprime les tâches terminées depuis plus de [days] jours (0 = jamais). */
    suspend fun purgeArchive(days: Int, now: Long) {
        if (days <= 0) return
        taskDao.purgeCompletedBefore(now - days * MILLIS_PER_DAY)
    }

    /** Cumule du temps de concentration (Pomodoro lié à une tâche). */
    suspend fun addFocusTime(taskId: Long, seconds: Int) {
        if (seconds > 0) taskDao.addFocusSeconds(taskId, seconds)
    }

    // --- Étiquettes -----------------------------------------------------------------------------

    suspend fun saveLabel(label: Label, now: Long): Long =
        if (label.id == 0L) labelDao.insert(label.copy(createdAt = now)) else {
            labelDao.update(label); label.id
        }

    suspend fun deleteLabel(id: Long) {
        labelDao.detachTasks(id)
        labelDao.deleteById(id)
    }

    /** Crée les étiquettes par défaut au premier lancement (si aucune n'existe). */
    suspend fun seedIfEmpty(now: Long) {
        if (labelDao.count() > 0) return
        listOf(
            "Perso" to "#4FA5A0",
            "Boulot" to "#E0A24E",
            "Maison" to "#9C9AA6",
        ).forEach { (name, color) -> labelDao.insert(Label(name = name, colorHex = color, createdAt = now)) }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
