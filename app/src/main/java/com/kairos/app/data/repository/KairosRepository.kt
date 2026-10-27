package com.kairos.app.data.repository

import com.kairos.app.data.local.AttachmentDao
import com.kairos.app.data.local.LabelDao
import com.kairos.app.data.local.SavedFilterDao
import com.kairos.app.data.local.SubtaskDao
import com.kairos.app.data.local.TaskDao
import com.kairos.app.data.model.Attachment
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.SavedFilter
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task
import com.kairos.app.domain.recurrence.Recurrence
import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique aux données (tâches, étiquettes, sous-tâches, filtres enregistrés, pièces
 * jointes) pour les ViewModels.
 */
class KairosRepository(
    private val taskDao: TaskDao,
    private val labelDao: LabelDao,
    private val subtaskDao: SubtaskDao,
    private val savedFilterDao: SavedFilterDao,
    private val attachmentDao: AttachmentDao,
    private val attachmentStore: AttachmentStore,
) {
    val tasks: Flow<List<Task>> = taskDao.observeAll()
    val labels: Flow<List<Label>> = labelDao.observeAll()
    val subtasks: Flow<List<Subtask>> = subtaskDao.observeAll()
    val savedFilters: Flow<List<SavedFilter>> = savedFilterDao.observeAll()
    val attachments: Flow<List<Attachment>> = attachmentDao.observeAll()

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
    suspend fun saveTaskWithSubtasks(
        task: Task,
        subtasks: List<Subtask>,
        now: Long,
        promoted: List<Subtask> = emptyList(),
        attachments: List<Attachment>? = null,
    ): Long {
        val id = saveTask(task, now)
        subtaskDao.deleteForTask(id)
        subtasks.forEachIndexed { index, sub ->
            subtaskDao.insert(sub.copy(id = 0, taskId = id, position = index))
        }
        // Sous-tâches **promues** : deviennent des tâches à part entière, dans le même quadrant et
        // avec la même étiquette que la tâche parente, en reprenant leur échéance propre.
        promoted.forEach { sub ->
            saveTask(
                Task(
                    title = sub.title,
                    important = task.important,
                    urgent = task.urgent,
                    labelId = task.labelId,
                    dueAt = sub.dueAt,
                    completedAt = if (sub.done) now else null,
                ),
                now,
            )
        }
        if (attachments != null) replaceAttachments(id, attachments, now)
        return id
    }

    /**
     * Aligne les pièces jointes de [taskId] sur [wanted] : supprime (ligne + fichier) celles qui n'y
     * sont plus, insère les nouvelles (id 0).
     */
    private suspend fun replaceAttachments(taskId: Long, wanted: List<Attachment>, now: Long) {
        val keptIds = wanted.map { it.id }.toSet()
        attachmentDao.forTask(taskId).filter { it.id !in keptIds }.forEach {
            attachmentDao.deleteById(it.id)
            attachmentStore.delete(it.fileName)
        }
        wanted.filter { it.id == 0L }.forEach {
            attachmentDao.insert(it.copy(taskId = taskId, createdAt = now))
        }
    }

    /** Copie un document choisi par l'utilisateur en pièce jointe (à rattacher à l'enregistrement). */
    suspend fun importAttachment(uri: android.net.Uri): Attachment = attachmentStore.import(uri)

    fun attachmentUri(attachment: Attachment): android.net.Uri = attachmentStore.uriFor(attachment)

    /** Supprime les fichiers de pièces jointes que plus aucune ligne ne référence (balayage au démarrage). */
    suspend fun sweepAttachmentFiles() = attachmentStore.sweep(attachmentDao.allFileNames().toSet())

    /**
     * Déplace une tâche dans un quadrant, devant [beforeId] (glisser) ou en fin (null : menu
     * accessible « Déplacer vers… »).
     */
    suspend fun moveTo(taskId: Long, quadrant: Quadrant, beforeId: Long? = null) {
        val task = taskDao.getById(taskId) ?: return
        if (task.important == quadrant.important && task.urgent == quadrant.urgent) return
        taskDao.placeInQuadrant(taskId, quadrant.important, quadrant.urgent, beforeId)
    }

    /**
     * Réordonne une tâche **dans son propre quadrant** : l'insère devant [beforeId] (null = en fin)
     * parmi les tâches actives du quadrant et renumérote les positions de façon dense.
     */
    suspend fun reorder(taskId: Long, beforeId: Long?) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.placeInQuadrant(taskId, task.important, task.urgent, beforeId)
    }

    /** Bascule urgent/important (accepter la suggestion d'échéance = passer urgent = true). */
    suspend fun setUrgent(taskId: Long, urgent: Boolean) {
        val task = taskDao.getById(taskId) ?: return
        if (task.urgent == urgent) return
        val position = taskDao.maxPosition(task.important, urgent) + 1
        taskDao.update(task.copy(urgent = urgent, position = position))
    }

    /**
     * Marque une tâche terminée ([completedAt] renseignée) ou active (null).
     *
     * Pour une tâche **récurrente**, la complétion ne l'archive **pas** : elle **avance sur place**
     * (échéance passée à la suivante, temps de focus remis à zéro, sous-tâches décochées) et reste
     * active sur le board. Il n'y a donc **jamais** qu'une seule occurrence vivante d'une récurrence
     * — pas d'accumulation dans l'archive, pas de doublon possible via restauration. Pour arrêter la
     * répétition, on retire la tâche à la main.
     */
    suspend fun setCompleted(taskId: Long, completedAt: Long?) {
        val task = taskDao.getById(taskId) ?: return
        if (completedAt != null && task.recurrenceType != Recurrence.NONE) {
            val nextDue = task.recurrenceType.next(task.dueAt ?: completedAt)
            taskDao.update(task.copy(dueAt = nextDue, completedAt = null, focusSeconds = 0))
            subtaskDao.forTask(task.id).forEach { sub ->
                if (sub.done) subtaskDao.update(sub.copy(done = false))
            }
            return
        }
        taskDao.update(task.copy(completedAt = completedAt))
    }

    /**
     * **Retire** une tâche du board (glissée hors, sans être faite) : elle part à l'archive avec
     * [removedAt]. Contrairement à la complétion, un retrait ne régénère **jamais** d'occurrence
     * récurrente. Passer `null` la réactive.
     */
    suspend fun setRemoved(taskId: Long, removedAt: Long?) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.update(task.copy(removedAt = removedAt))
    }

    /** Restaure une tâche depuis l'archive (terminée **ou** retirée) : elle revient sur le board. */
    suspend fun restoreFromArchive(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.update(task.copy(completedAt = null, removedAt = null))
    }

    // --- Actions en lot (sélection multiple) ----------------------------------------------------

    /** Déplace plusieurs tâches en fin de [quadrant] (celles qui y sont déjà ne bougent pas). */
    suspend fun moveMany(ids: List<Long>, quadrant: Quadrant) = ids.forEach { moveTo(it, quadrant) }

    suspend fun setLabelMany(ids: List<Long>, labelId: Long?) = taskDao.setLabel(ids, labelId)

    suspend fun completeMany(ids: List<Long>, now: Long) = ids.forEach { setCompleted(it, now) }

    suspend fun removeMany(ids: List<Long>, now: Long) = ids.forEach { setRemoved(it, now) }

    // --- Time-blocking --------------------------------------------------------------------------

    /** Planifie [taskId] sur le créneau [start] (null = retire du planning) pour [minutes]. */
    suspend fun setBlock(taskId: Long, start: Long?, minutes: Int) =
        taskDao.setBlock(taskId, start, if (start == null) 0 else minutes)

    /** Suppression **définitive** (uniquement depuis l'archive). */
    suspend fun deleteTask(taskId: Long) = taskDao.deleteById(taskId)

    /** Vide l'archive (toutes les tâches terminées ou retirées). */
    suspend fun clearArchive() = taskDao.clearArchived()

    /** Purge automatique : supprime les tâches archivées depuis plus de [days] jours (0 = jamais). */
    suspend fun purgeArchive(days: Int, now: Long) {
        if (days <= 0) return
        taskDao.purgeArchivedBefore(now - days * MILLIS_PER_DAY)
    }

    /** Nettoyage unique : purge les occurrences récurrentes terminées héritées de l'ancien modèle. */
    suspend fun purgeLegacyRecurringArchive() = taskDao.purgeLegacyRecurringCompleted()

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
        savedFilterDao.detachLabel(id)
        labelDao.deleteById(id)
    }

    suspend fun setLabelPinned(id: Long, pinned: Boolean) = labelDao.setPinned(id, pinned)

    // --- Filtres enregistrés --------------------------------------------------------------------

    suspend fun saveFilter(filter: SavedFilter, now: Long): Long =
        savedFilterDao.insert(filter.copy(createdAt = if (filter.id == 0L) now else filter.createdAt))

    suspend fun deleteFilter(id: Long) = savedFilterDao.deleteById(id)

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
