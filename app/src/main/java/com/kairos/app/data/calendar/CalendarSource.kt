package com.kairos.app.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Créneau **occupé** d'un agenda externe (lecture seule) : sert de repère pour le time-blocking.
 * [color] = couleur ARGB de l'agenda (null pour un flux iCal).
 */
data class BusySlot(
    val start: Long,
    val end: Long,
    val title: String,
    val allDay: Boolean,
    val color: Int? = null,
)

/**
 * Lit les créneaux occupés de deux sources, fusionnées :
 * - les **agendas de l'appareil** (`CalendarContract`) — couvre Google Agenda et tout compte
 *   synchronisé sur le téléphone ; nécessite la permission `READ_CALENDAR` ;
 * - un **flux iCal** (.ics, http(s) ou webcal) optionnel, téléchargé puis mis en cache 15 min.
 *
 * Les événements marqués « disponible » (transparents) ou annulés sont ignorés.
 */
class CalendarSource(private val context: Context, private val client: OkHttpClient) {

    private var icalCache: Triple<String, Long, List<IcalEvent>>? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Créneaux occupés intersectant [from, to[, triés par début. Une source en échec est ignorée. */
    suspend fun busySlots(from: Long, to: Long, deviceCalendars: Boolean, icalUrl: String): List<BusySlot> {
        val device = if (deviceCalendars && hasPermission()) {
            runCatching { deviceSlots(from, to) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val ical = if (icalUrl.isNotBlank()) {
            runCatching { icalEvents(icalUrl).flatMap { it.occurrences(from, to) } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return (device + ical).sortedBy { it.start }
    }

    /** Vérifie un flux iCal (téléchargement + lecture) et renvoie son nombre d'événements. */
    suspend fun testIcal(url: String): Result<Int> = runCatching {
        icalCache = null
        icalEvents(url).size
    }

    private suspend fun deviceSlots(from: Long, to: Long): List<BusySlot> = withContext(Dispatchers.IO) {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.AVAILABILITY,
            CalendarContract.Instances.STATUS,
        )
        val result = mutableListOf<BusySlot>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(5) == CalendarContract.Events.AVAILABILITY_FREE) continue
                if (c.getInt(6) == CalendarContract.Events.STATUS_CANCELED) continue
                result += BusySlot(
                    start = c.getLong(0),
                    end = c.getLong(1),
                    title = c.getString(2).orEmpty(),
                    allDay = c.getInt(3) != 0,
                    color = if (c.isNull(4)) null else c.getInt(4),
                )
            }
        }
        result
    }

    private suspend fun icalEvents(url: String): List<IcalEvent> {
        val normalized = url.trim().replaceFirst(Regex("^webcals?://", RegexOption.IGNORE_CASE), "https://")
        val now = System.currentTimeMillis()
        icalCache?.let { (cachedUrl, at, events) ->
            if (cachedUrl == normalized && now - at < CACHE_MILLIS) return events
        }
        val body = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(normalized).build()).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body?.string().orEmpty()
            }
        }
        check(body.contains("BEGIN:VCALENDAR")) { "Not an iCal feed" }
        val events = IcalParser.parse(body)
        icalCache = Triple(normalized, now, events)
        return events
    }

    private companion object {
        const val CACHE_MILLIS = 15L * 60 * 1000
    }
}
