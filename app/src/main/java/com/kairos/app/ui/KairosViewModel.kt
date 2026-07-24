package com.kairos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task
import com.kairos.app.data.repository.KairosRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Avancement d'une checklist (pour le compteur sur la ligne de tâche). */
data class SubtaskProgress(val done: Int, val total: Int)

/** ViewModel unique de l'app (petite taille) : tâches + étiquettes + sous-tâches et leurs actions. */
class KairosViewModel(private val repository: KairosRepository) : ViewModel() {

    val tasks: StateFlow<List<Task>> =
        repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val labels: StateFlow<List<Label>> =
        repository.labels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subtasks: StateFlow<List<Subtask>> =
        repository.subtasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Compteur terminées/total par tâche, dérivé du flux des sous-tâches. */
    val subtaskProgress: StateFlow<Map<Long, SubtaskProgress>> =
        repository.subtasks
            .map { list ->
                list.groupBy { it.taskId }
                    .mapValues { (_, subs) -> SubtaskProgress(subs.count { it.done }, subs.size) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun now() = System.currentTimeMillis()

    /** Occurrence régénérée à la dernière complétion d'une tâche récurrente : (idTâcheParent, idOccurrence). */
    private var lastSpawn: Pair<Long, Long>? = null

    /** Sous-tâches de la dernière tâche supprimée (pour les restaurer à l'annulation). */
    private var lastDeletedTaskId: Long? = null
    private var lastDeletedSubtasks: List<Subtask> = emptyList()

    fun saveTask(task: Task) = viewModelScope.launch { repository.saveTask(task, now()) }

    fun saveTask(task: Task, subtasks: List<Subtask>) =
        viewModelScope.launch { repository.saveTaskWithSubtasks(task, subtasks, now()) }

    fun moveTo(taskId: Long, quadrant: Quadrant) = viewModelScope.launch { repository.moveTo(taskId, quadrant) }

    fun reorder(taskId: Long, targetIndex: Int) = viewModelScope.launch { repository.reorder(taskId, targetIndex) }

    fun setUrgent(taskId: Long, urgent: Boolean) = viewModelScope.launch { repository.setUrgent(taskId, urgent) }

    fun setCompleted(taskId: Long, completed: Boolean) = viewModelScope.launch {
        val spawnedId = repository.setCompleted(taskId, if (completed) now() else null, now())
        lastSpawn = if (completed && spawnedId != null) taskId to spawnedId else null
    }

    /**
     * Annule une complétion : réactive la tâche et, si sa complétion avait régénéré une occurrence
     * (tâche récurrente), supprime cette occurrence pour éviter un doublon.
     */
    fun undoComplete(task: Task) = viewModelScope.launch {
        repository.setCompleted(task.id, null, now())
        lastSpawn?.let { (parentId, spawnedId) ->
            if (parentId == task.id) {
                repository.deleteTask(spawnedId)
                lastSpawn = null
            }
        }
    }

    fun deleteTask(taskId: Long) = viewModelScope.launch {
        // Capture la checklist avant la suppression (cascade FK) pour pouvoir l'annuler.
        lastDeletedSubtasks = repository.getSubtasks(taskId)
        lastDeletedTaskId = taskId
        repository.deleteTask(taskId)
    }

    fun restoreTask(task: Task) = viewModelScope.launch {
        val subtasks = if (lastDeletedTaskId == task.id) lastDeletedSubtasks else emptyList()
        repository.restoreTask(task, subtasks)
    }

    fun clearArchive() = viewModelScope.launch { repository.clearArchive() }

    fun saveLabel(label: Label) = viewModelScope.launch { repository.saveLabel(label, now()) }

    fun deleteLabel(id: Long) = viewModelScope.launch { repository.deleteLabel(id) }
}
