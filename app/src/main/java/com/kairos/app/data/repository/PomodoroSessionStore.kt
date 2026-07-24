package com.kairos.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.pomodoroDataStore by preferencesDataStore(name = "pomodoro_session")

/**
 * Persiste la session Pomodoro en cours pour qu'elle **survive au redémarrage du process**.
 * Le décompte est ancré sur un **instant de fin absolu** ([Snapshot.endAt]) : au rechargement, le
 * temps restant se recalcule à partir de l'horloge (robuste, pas de dérive). Repris de WorkSync.
 */
class PomodoroSessionStore(private val context: Context) {

    data class Snapshot(
        val phaseOrdinal: Int,
        val totalSeconds: Int,
        val remainingSeconds: Int,
        val isRunning: Boolean,
        /** Instant de fin (epoch ms) si en cours ; 0 si en pause. */
        val endAt: Long,
        val completedFocus: Int,
    )

    private object Keys {
        val PHASE = intPreferencesKey("phase")
        val TOTAL = intPreferencesKey("total")
        val REMAINING = intPreferencesKey("remaining")
        val RUNNING = booleanPreferencesKey("running")
        val END_AT = longPreferencesKey("end_at")
        val COMPLETED = intPreferencesKey("completed")
        val HAS = booleanPreferencesKey("has_session")
    }

    suspend fun save(snapshot: Snapshot) {
        context.pomodoroDataStore.edit { prefs ->
            prefs[Keys.HAS] = true
            prefs[Keys.PHASE] = snapshot.phaseOrdinal
            prefs[Keys.TOTAL] = snapshot.totalSeconds
            prefs[Keys.REMAINING] = snapshot.remainingSeconds
            prefs[Keys.RUNNING] = snapshot.isRunning
            prefs[Keys.END_AT] = snapshot.endAt
            prefs[Keys.COMPLETED] = snapshot.completedFocus
        }
    }

    suspend fun load(): Snapshot? {
        val prefs = context.pomodoroDataStore.data.first()
        if (prefs[Keys.HAS] != true) return null
        return Snapshot(
            phaseOrdinal = prefs[Keys.PHASE] ?: 0,
            totalSeconds = prefs[Keys.TOTAL] ?: 0,
            remainingSeconds = prefs[Keys.REMAINING] ?: 0,
            isRunning = prefs[Keys.RUNNING] ?: false,
            endAt = prefs[Keys.END_AT] ?: 0L,
            completedFocus = prefs[Keys.COMPLETED] ?: 0,
        )
    }

    suspend fun clear() {
        context.pomodoroDataStore.edit { it.clear() }
    }
}
