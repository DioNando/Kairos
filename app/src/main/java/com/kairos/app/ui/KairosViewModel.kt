package com.kairos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.kairos.app.data.model.Attachment
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.SavedFilter
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

    val savedFilters: StateFlow<List<SavedFilter>> =
        repository.savedFilters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val attachments: StateFlow<List<Attachment>> =
        repository.attachments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /**
     * Enregistre depuis l'éditeur : checklist, sous-tâches [promoted] (créées comme tâches) et pièces
     * jointes ([attachments] = liste complète voulue).
     */
    fun saveTask(
        task: Task,
        subtasks: List<Subtask>,
        promoted: List<Subtask> = emptyList(),
        attachments: List<Attachment>? = null,
    ) = viewModelScope.launch {
        repository.saveTaskWithSubtasks(task, subtasks, now(), promoted, attachments)
    }

    suspend fun importAttachment(uri: Uri): Result<Attachment> = runCatching { repository.importAttachment(uri) }

    fun attachmentUri(attachment: Attachment): Uri = repository.attachmentUri(attachment)

    // --- Sélection multiple ---------------------------------------------------------------------

    fun moveMany(ids: Collection<Long>, quadrant: Quadrant) =
        viewModelScope.launch { repository.moveMany(ids.toList(), quadrant) }

    fun setLabelMany(ids: Collection<Long>, labelId: Long?) =
        viewModelScope.launch { repository.setLabelMany(ids.toList(), labelId) }

    fun completeMany(ids: Collection<Long>) = viewModelScope.launch { repository.completeMany(ids.toList(), now()) }

    fun removeMany(ids: Collection<Long>) = viewModelScope.launch { repository.removeMany(ids.toList(), now()) }

    // --- Time-blocking --------------------------------------------------------------------------

    fun setBlock(taskId: Long, start: Long?, minutes: Int) =
        viewModelScope.launch { repository.setBlock(taskId, start, minutes) }

    // --- Étiquettes & filtres -------------------------------------------------------------------

    fun reorderLabels(ids: List<Long>) = viewModelScope.launch { repository.reorderLabels(ids) }

    fun setLabelPinned(id: Long, pinned: Boolean) = viewModelScope.launch { repository.setLabelPinned(id, pinned) }

    fun saveFilter(filter: SavedFilter) = viewModelScope.launch { repository.saveFilter(filter, now()) }

    fun deleteFilter(id: Long) = viewModelScope.launch { repository.deleteFilter(id) }

    fun moveTo(taskId: Long, quadrant: Quadrant, beforeId: Long? = null) =
        viewModelScope.launch { repository.moveTo(taskId, quadrant, beforeId) }

    fun reorder(taskId: Long, beforeId: Long?) = viewModelScope.launch { repository.reorder(taskId, beforeId) }

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
