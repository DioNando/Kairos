package com.kairos.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairos.app.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kairos_settings")

/** Persiste les préférences (thème, palette, seuil d'urgence) via DataStore et les expose en Flow. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val LANGUAGE = intPreferencesKey("language")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PALETTE = intPreferencesKey("palette_id")
        val URGENCY_THRESHOLD = intPreferencesKey("urgency_threshold_hours")
        val SWIM_LANES = booleanPreferencesKey("swim_lanes")
        val ARCHIVE_PURGE_DAYS = intPreferencesKey("archive_purge_days")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SHOW_GUIDE = booleanPreferencesKey("show_guide")
        val CUSTOM_DO = stringPreferencesKey("custom_do_color")
        val CUSTOM_SCHEDULE = stringPreferencesKey("custom_schedule_color")
        val CUSTOM_DELEGATE = stringPreferencesKey("custom_delegate_color")
        val CUSTOM_ELIMINATE = stringPreferencesKey("custom_eliminate_color")
        val POMODORO_LIQUID = booleanPreferencesKey("pomodoro_liquid")
        val FOCUS_MIN = intPreferencesKey("pomodoro_focus_min")
        val SHORT_BREAK_MIN = intPreferencesKey("pomodoro_short_break_min")
        val LONG_BREAK_MIN = intPreferencesKey("pomodoro_long_break_min")
        val CYCLES_BEFORE_LONG = intPreferencesKey("pomodoro_cycles_before_long")
        val AI_PROVIDER = intPreferencesKey("ai_provider")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_LEAD_MIN = intPreferencesKey("reminder_lead_minutes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: 0,
            language = prefs[Keys.LANGUAGE] ?: AppSettings.LANG_SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            paletteId = prefs[Keys.PALETTE] ?: 0,
            urgencyThresholdHours = prefs[Keys.URGENCY_THRESHOLD] ?: 48,
            swimLanes = prefs[Keys.SWIM_LANES] ?: false,
            archivePurgeDays = prefs[Keys.ARCHIVE_PURGE_DAYS] ?: 0,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            showGuide = prefs[Keys.SHOW_GUIDE] ?: true,
            customDoColor = prefs[Keys.CUSTOM_DO] ?: "#E5645A",
            customScheduleColor = prefs[Keys.CUSTOM_SCHEDULE] ?: "#4FA5A0",
            customDelegateColor = prefs[Keys.CUSTOM_DELEGATE] ?: "#E0A24E",
            customEliminateColor = prefs[Keys.CUSTOM_ELIMINATE] ?: "#9C9AA6",
            pomodoroLiquid = prefs[Keys.POMODORO_LIQUID] ?: true,
            focusMinutes = prefs[Keys.FOCUS_MIN] ?: 25,
            shortBreakMinutes = prefs[Keys.SHORT_BREAK_MIN] ?: 5,
            longBreakMinutes = prefs[Keys.LONG_BREAK_MIN] ?: 15,
            cyclesBeforeLongBreak = prefs[Keys.CYCLES_BEFORE_LONG] ?: 4,
            aiProvider = prefs[Keys.AI_PROVIDER] ?: AppSettings.AI_GEMINI,
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
            reminderLeadMinutes = prefs[Keys.REMINDER_LEAD_MIN] ?: 0,
        )
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.coerceIn(0, 2) }
    }

    suspend fun setLanguage(language: Int) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.coerceIn(0, 2) }
    }

    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
    }

    suspend fun setPalette(id: Int) {
        context.dataStore.edit { it[Keys.PALETTE] = id }
    }

    suspend fun setUrgencyThreshold(hours: Int) {
        context.dataStore.edit { it[Keys.URGENCY_THRESHOLD] = hours.coerceIn(0, 24 * 14) }
    }

    suspend fun setSwimLanes(value: Boolean) {
        context.dataStore.edit { it[Keys.SWIM_LANES] = value }
    }

    suspend fun setArchivePurgeDays(days: Int) {
        context.dataStore.edit { it[Keys.ARCHIVE_PURGE_DAYS] = days.coerceIn(0, 365) }
    }

    suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = value }
    }

    suspend fun setShowGuide(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_GUIDE] = value }
    }

    /** Enregistre les 4 couleurs de la palette personnalisée (chaînes « #RRGGBB »). */
    suspend fun setCustomPalette(doColor: String, schedule: String, delegate: String, eliminate: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_DO] = doColor
            prefs[Keys.CUSTOM_SCHEDULE] = schedule
            prefs[Keys.CUSTOM_DELEGATE] = delegate
            prefs[Keys.CUSTOM_ELIMINATE] = eliminate
        }
    }

    suspend fun setPomodoroStyle(liquid: Boolean) {
        context.dataStore.edit { it[Keys.POMODORO_LIQUID] = liquid }
    }

    suspend fun setAiProvider(provider: Int) {
        context.dataStore.edit { it[Keys.AI_PROVIDER] = provider.coerceIn(0, 1) }
    }

    suspend fun setRemindersEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.REMINDERS_ENABLED] = value }
    }

    suspend fun setReminderLeadMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.REMINDER_LEAD_MIN] = minutes.coerceIn(0, 24 * 60) }
    }

    /** Enregistre les 4 durées Pomodoro (valeurs bornées pour rester utilisables). */
    suspend fun setPomodoroDurations(focus: Int, shortBreak: Int, longBreak: Int, cycles: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FOCUS_MIN] = focus.coerceIn(1, 180)
            prefs[Keys.SHORT_BREAK_MIN] = shortBreak.coerceIn(1, 60)
            prefs[Keys.LONG_BREAK_MIN] = longBreak.coerceIn(1, 60)
            prefs[Keys.CYCLES_BEFORE_LONG] = cycles.coerceIn(1, 12)
        }
    }

    /** Restaure d'un bloc l'ensemble des réglages (import d'une sauvegarde). */
    suspend fun applyAll(s: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = s.themeMode
            prefs[Keys.LANGUAGE] = s.language
            prefs[Keys.DYNAMIC_COLOR] = s.dynamicColor
            prefs[Keys.PALETTE] = s.paletteId
            prefs[Keys.URGENCY_THRESHOLD] = s.urgencyThresholdHours
            prefs[Keys.SWIM_LANES] = s.swimLanes
            prefs[Keys.ARCHIVE_PURGE_DAYS] = s.archivePurgeDays
            prefs[Keys.ONBOARDING_DONE] = s.onboardingDone
            prefs[Keys.SHOW_GUIDE] = s.showGuide
            prefs[Keys.CUSTOM_DO] = s.customDoColor
            prefs[Keys.CUSTOM_SCHEDULE] = s.customScheduleColor
            prefs[Keys.CUSTOM_DELEGATE] = s.customDelegateColor
            prefs[Keys.CUSTOM_ELIMINATE] = s.customEliminateColor
            prefs[Keys.POMODORO_LIQUID] = s.pomodoroLiquid
            prefs[Keys.FOCUS_MIN] = s.focusMinutes
            prefs[Keys.SHORT_BREAK_MIN] = s.shortBreakMinutes
            prefs[Keys.LONG_BREAK_MIN] = s.longBreakMinutes
            prefs[Keys.CYCLES_BEFORE_LONG] = s.cyclesBeforeLongBreak
            prefs[Keys.AI_PROVIDER] = s.aiProvider
            prefs[Keys.REMINDERS_ENABLED] = s.remindersEnabled
            prefs[Keys.REMINDER_LEAD_MIN] = s.reminderLeadMinutes
        }
    }
}
