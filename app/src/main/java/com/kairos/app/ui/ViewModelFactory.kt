package com.kairos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kairos.app.data.repository.KairosRepository
import com.kairos.app.ui.pomodoro.PomodoroEngine
import com.kairos.app.ui.pomodoro.PomodoroViewModel

/**
 * Fabrique unique injectant le repository partagé et le moteur Pomodoro dans les ViewModels.
 * Évite d'ajouter Hilt pour une app de cette taille.
 */
class ViewModelFactory(
    private val repository: KairosRepository,
    private val pomodoroEngine: PomodoroEngine,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(KairosViewModel::class.java) ->
            KairosViewModel(repository) as T

        modelClass.isAssignableFrom(PomodoroViewModel::class.java) ->
            PomodoroViewModel(pomodoroEngine) as T

        else -> throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
    }
}
