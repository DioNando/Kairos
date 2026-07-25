package com.kairos.app.data.model

/**
 * Préférences applicatives persistées via DataStore.
 *
 * @param themeMode 0 = système, 1 = clair, 2 = sombre.
 * @param dynamicColor active Material You (Android 12+).
 * @param paletteId index dans `AppPalettes`.
 * @param urgencyThresholdHours nombre d'heures avant l'échéance en deçà duquel une tâche
 *   « pulse » (échéance proche) et propose de passer en urgent. 0 = jamais.
 */
data class AppSettings(
    val themeMode: Int = 0,
    /** Langue de l'app : 0 = système, 1 = français, 2 = anglais. */
    val language: Int = LANG_SYSTEM,
    val dynamicColor: Boolean = false,
    val paletteId: Int = 0,
    val urgencyThresholdHours: Int = 48,
    /** false = grille 2×2, true = vue « swim lanes » (liste verticale par quadrant). */
    val swimLanes: Boolean = false,
    /** Purge auto des tâches archivées après N jours. 0 = jamais. */
    val archivePurgeDays: Int = 0,
    /** true une fois l'écran d'accueil (onboarding) vu au premier lancement. */
    val onboardingDone: Boolean = false,
    /** Affiche le raccourci « Guide » dans la barre basse de la matrice. */
    val showGuide: Boolean = true,
    // --- Palette personnalisée (utilisée quand paletteId == index custom) ---
    val customDoColor: String = "#E5645A",
    val customScheduleColor: String = "#4FA5A0",
    val customDelegateColor: String = "#E0A24E",
    val customEliminateColor: String = "#9C9AA6",
    // --- Pomodoro (repris de WorkSync) ---
    /** true = minuteur « cercle liquide », false = anneau de progression. */
    val pomodoroLiquid: Boolean = true,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    // --- Assistant IA (reformulation des notes) ---
    /** Fournisseur IA primaire : 0 = Gemini, 1 = OpenRouter (l'autre sert de repli). */
    val aiProvider: Int = AI_GEMINI,
    // --- Rappels d'échéance ---
    /** Notifie à l'approche de l'échéance des tâches actives qui en ont une. */
    val remindersEnabled: Boolean = true,
    /** Délai (minutes) **avant** l'échéance auquel notifier. 0 = pile à l'heure. */
    val reminderLeadMinutes: Int = 0,
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val AI_GEMINI = 0
        const val AI_OPENROUTER = 1

        const val LANG_SYSTEM = 0
        const val LANG_FR = 1
        const val LANG_EN = 2
    }
}
