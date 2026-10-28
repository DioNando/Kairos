package com.kairos.app.domain.backup

import com.kairos.app.data.local.LabelDao
import com.kairos.app.data.local.SavedFilterDao
import com.kairos.app.data.local.SubtaskDao
import com.kairos.app.data.local.TaskDao
import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.SavedFilter
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task
import com.kairos.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde et restauration **locale** de toutes les données (JSON auto-suffisant et versionné).
 * Mapping manuel via `org.json` (aucune dépendance de sérialisation ajoutée).
 *
 * L'import **remplace** l'intégralité des données (clear + réinsertion en conservant les ids, ce qui
 * préserve le lien tâche↔étiquette). Le JSON est entièrement parsé **avant** tout effacement : un
 * fichier corrompu lève une exception sans toucher à la base.
 *
 * Les pièces jointes ne sont **pas** incluses (fichiers binaires) : seules les données texte voyagent.
 */
class BackupManager(
    private val taskDao: TaskDao,
    private val labelDao: LabelDao,
    private val subtaskDao: SubtaskDao,
    private val savedFilterDao: SavedFilterDao,
    private val settingsRepository: SettingsRepository,
) {

    /** Bilan d'un import réussi, pour le retour utilisateur. */
    data class ImportResult(val tasks: Int, val labels: Int)

    // --- Export --------------------------------------------------------------------------------

    suspend fun export(): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)

        root.put("labels", labelDao.getAll().toJsonArray { l ->
            JSONObject().apply {
                put("id", l.id)
                put("name", l.name)
                put("colorHex", l.colorHex)
                put("createdAt", l.createdAt)
                put("groupName", l.groupName)
                put("pinned", l.pinned)
                put("position", l.position)
            }
        })

        root.put("savedFilters", savedFilterDao.getAll().toJsonArray { f ->
            JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("query", f.query)
                if (f.labelId != null) put("labelId", f.labelId)
                put("createdAt", f.createdAt)
            }
        })

        root.put("tasks", taskDao.getAll().toJsonArray { t ->
            JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("note", t.note)
                put("important", t.important)
                put("urgent", t.urgent)
                if (t.dueAt != null) put("dueAt", t.dueAt)
                if (t.labelId != null) put("labelId", t.labelId)
                put("position", t.position)
                put("createdAt", t.createdAt)
                if (t.completedAt != null) put("completedAt", t.completedAt)
                if (t.removedAt != null) put("removedAt", t.removedAt)
                put("recurrence", t.recurrence)
                put("focusSeconds", t.focusSeconds)
                if (t.blockStart != null) put("blockStart", t.blockStart)
                put("blockMinutes", t.blockMinutes)
            }
        })

        root.put("subtasks", subtaskDao.getAll().toJsonArray { s ->
            JSONObject().apply {
                put("id", s.id)
                put("taskId", s.taskId)
                put("title", s.title)
                put("done", s.done)
                put("position", s.position)
                if (s.dueAt != null) put("dueAt", s.dueAt)
            }
        })

        root.put("settings", settingsRepository.settings.first().toJson())

        return root.toString(2)
    }

    // --- Import --------------------------------------------------------------------------------

    suspend fun import(jsonText: String): ImportResult {
        val root = JSONObject(jsonText)

        // 1) Tout parser d'abord (échec = base intacte).
        val labels = root.optJSONArray("labels").mapObjects { o ->
            Label(
                id = o.getLong("id"),
                name = o.getString("name"),
                colorHex = o.optString("colorHex", "#9C9AA6"),
                createdAt = o.optLong("createdAt"),
                groupName = o.optString("groupName", ""),
                pinned = o.optBoolean("pinned"),
                position = o.optInt("position"),
            )
        }
        val savedFilters = root.optJSONArray("savedFilters").mapObjects { o ->
            SavedFilter(
                id = o.getLong("id"),
                name = o.getString("name"),
                query = o.optString("query", ""),
                labelId = o.optLongOrNull("labelId"),
                createdAt = o.optLong("createdAt"),
            )
        }
        val tasks = root.optJSONArray("tasks").mapObjects { o ->
            Task(
                id = o.getLong("id"),
                title = o.getString("title"),
                note = o.optString("note", ""),
                important = o.optBoolean("important"),
                urgent = o.optBoolean("urgent"),
                dueAt = if (o.has("dueAt") && !o.isNull("dueAt")) o.getLong("dueAt") else null,
                labelId = if (o.has("labelId") && !o.isNull("labelId")) o.getLong("labelId") else null,
                position = o.optInt("position"),
                createdAt = o.optLong("createdAt"),
                completedAt = if (o.has("completedAt") && !o.isNull("completedAt")) o.getLong("completedAt") else null,
                removedAt = if (o.has("removedAt") && !o.isNull("removedAt")) o.getLong("removedAt") else null,
                recurrence = o.optInt("recurrence"),
                focusSeconds = o.optInt("focusSeconds"),
                blockStart = o.optLongOrNull("blockStart"),
                blockMinutes = o.optInt("blockMinutes"),
            )
        }
        val subtasks = root.optJSONArray("subtasks").mapObjects { o ->
            Subtask(
                id = o.getLong("id"),
                taskId = o.getLong("taskId"),
                title = o.getString("title"),
                done = o.optBoolean("done"),
                position = o.optInt("position"),
                dueAt = o.optLongOrNull("dueAt"),
            )
        }
        val settings = root.optJSONObject("settings")?.toSettings()

        // 2) Remplacer : vider puis réinsérer en conservant les ids.
        subtaskDao.clear()
        taskDao.clear()
        savedFilterDao.clear()
        labelDao.clear()
        labels.forEach { labelDao.insert(it) }
        savedFilters.forEach { savedFilterDao.insert(it) }
        tasks.forEach { taskDao.insert(it) }
        subtasks.forEach { subtaskDao.insert(it) }
        settings?.let { settingsRepository.applyAll(it) }

        return ImportResult(tasks = tasks.size, labels = labels.size)
    }

    // --- Sérialisation des réglages ------------------------------------------------------------

    private fun AppSettings.toJson(): JSONObject = JSONObject().apply {
        put("themeMode", themeMode)
        put("language", language)
        put("dynamicColor", dynamicColor)
        put("paletteId", paletteId)
        put("urgencyThresholdHours", urgencyThresholdHours)
        put("swimLanes", swimLanes)
        put("archivePurgeDays", archivePurgeDays)
        put("onboardingDone", onboardingDone)
        put("showGuide", showGuide)
        put("customDoColor", customDoColor)
        put("customScheduleColor", customScheduleColor)
        put("customDelegateColor", customDelegateColor)
        put("customEliminateColor", customEliminateColor)
        put("pomodoroLiquid", pomodoroLiquid)
        put("focusMinutes", focusMinutes)
        put("shortBreakMinutes", shortBreakMinutes)
        put("longBreakMinutes", longBreakMinutes)
        put("cyclesBeforeLongBreak", cyclesBeforeLongBreak)
        put("aiProvider", aiProvider)
        put("remindersEnabled", remindersEnabled)
        put("reminderLeadMinutes", reminderLeadMinutes)
        put("quadrantSort", quadrantSort)
        put("focusDueWeight", focusDueWeight)
        put("calendarEnabled", calendarEnabled)
        put("icalUrl", icalUrl)
    }

    private fun JSONObject.toSettings(): AppSettings = AppSettings(
        themeMode = optInt("themeMode"),
        language = optInt("language"),
        dynamicColor = optBoolean("dynamicColor"),
        paletteId = optInt("paletteId"),
        urgencyThresholdHours = optInt("urgencyThresholdHours", 48),
        swimLanes = optBoolean("swimLanes"),
        archivePurgeDays = optInt("archivePurgeDays"),
        onboardingDone = optBoolean("onboardingDone"),
        showGuide = optBoolean("showGuide", true),
        customDoColor = optString("customDoColor", "#E5645A"),
        customScheduleColor = optString("customScheduleColor", "#4FA5A0"),
        customDelegateColor = optString("customDelegateColor", "#E0A24E"),
        customEliminateColor = optString("customEliminateColor", "#9C9AA6"),
        pomodoroLiquid = optBoolean("pomodoroLiquid", true),
        focusMinutes = optInt("focusMinutes", 25),
        shortBreakMinutes = optInt("shortBreakMinutes", 5),
        longBreakMinutes = optInt("longBreakMinutes", 15),
        cyclesBeforeLongBreak = optInt("cyclesBeforeLongBreak", 4),
        aiProvider = optInt("aiProvider"),
        remindersEnabled = optBoolean("remindersEnabled", true),
        reminderLeadMinutes = optInt("reminderLeadMinutes", 0),
        quadrantSort = optInt("quadrantSort", AppSettings.SORT_MANUAL),
        focusDueWeight = optInt("focusDueWeight", 30),
        calendarEnabled = optBoolean("calendarEnabled"),
        icalUrl = optString("icalUrl", ""),
    )

    private companion object {
        const val FORMAT_VERSION = 3
    }
}

// --- Helpers org.json --------------------------------------------------------------------------

private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
    JSONArray().also { arr -> forEach { arr.put(transform(it)) } }

private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { transform(getJSONObject(it)) }
}
