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

    fun saveTask(task: Task) = viewModelScope.launch { repository.saveTask(task, now()) }

    fun saveTask(task: Task, subtasks: List<Subtask>) =
        viewModelScope.launch { repository.saveTaskWithSubtasks(task, subtasks, now()) }

    fun moveTo(taskId: Long, quadrant: Quadrant) = viewModelScope.launch { repository.moveTo(taskId, quadrant) }

    fun reorder(taskId: Long, targetIndex: Int) = viewModelScope.launch { repository.reorder(taskId, targetIndex) }

    fun setUrgent(taskId: Long, urgent: Boolean) = viewModelScope.launch { repository.setUrgent(taskId, urgent) }

    /**
     * Marque terminée ou réactive. Pour une tâche récurrente, la complétion l'**avance sur place**
     * (échéance suivante, sous-tâches décochées) au lieu de l'archiver → une seule occurrence vivante.
     */
    fun setCompleted(taskId: Long, completed: Boolean) = viewModelScope.launch {
        repository.setCompleted(taskId, if (completed) now() else null)
    }

    /** Retire une tâche du board → archive (réversible depuis l'archive, sans récurrence). */
    fun removeTask(taskId: Long) = viewModelScope.launch { repository.setRemoved(taskId, now()) }

    /** Restaure une tâche archivée (terminée ou retirée) : elle revient sur le board. */
    fun restoreFromArchive(taskId: Long) = viewModelScope.launch { repository.restoreFromArchive(taskId) }

    /** Suppression **définitive** (uniquement depuis l'archive). */
    fun deleteTask(taskId: Long) = viewModelScope.launch { repository.deleteTask(taskId) }

    fun clearArchive() = viewModelScope.launch { repository.clearArchive() }

    fun saveLabel(label: Label) = viewModelScope.launch { repository.saveLabel(label, now()) }

    fun deleteLabel(id: Long) = viewModelScope.launch { repository.deleteLabel(id) }
}
