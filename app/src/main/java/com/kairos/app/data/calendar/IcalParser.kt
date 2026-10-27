package com.kairos.app.data.calendar

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Événement iCal réduit à ce qui sert au time-blocking : bornes, titre et récurrence simple.
 * [rrule] = règle brute (`FREQ=…;…`) ou null ; [exdates] = occurrences exclues (début, epoch millis).
 */
data class IcalEvent(
    val start: ZonedDateTime,
    val duration: Duration,
    val title: String,
    val allDay: Boolean,
    val rrule: String? = null,
    val exdates: Set<Long> = emptySet(),
) {
    /**
     * Occurrences intersectant [from, to[. Récurrences prises en charge : FREQ DAILY / WEEKLY
     * (avec BYDAY) / MONTHLY / YEARLY, INTERVAL, COUNT et UNTIL — de quoi couvrir les agendas
     * usuels (réunions hebdo, rituels quotidiens). Les règles plus exotiques retombent sur la
     * première occurrence.
     */
    fun occurrences(from: Long, to: Long): List<BusySlot> {
        val starts = if (rrule == null) sequenceOf(start) else expand(rrule, to)
        return starts
            .map { it.toInstant().toEpochMilli() }
            .filter { it !in exdates }
            .map { s -> s to s + duration.toMillis() }
            .filter { (s, e) -> e > from && s < to }
            .map { (s, e) -> BusySlot(s, e, title, allDay) }
            .toList()
    }

    private fun expand(rule: String, to: Long): Sequence<ZonedDateTime> {
        val parts = rule.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).uppercase() to it.substring(i + 1)
        }.toMap()
        val interval = parts["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { IcalParser.parseDateTime(it, emptyMap(), start.zone)?.first }
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { IcalParser.dayOf(it.takeLast(2)) }?.toSet()
        val unit = when (parts["FREQ"]?.uppercase()) {
            "DAILY" -> ChronoUnit.DAYS
            "WEEKLY" -> ChronoUnit.WEEKS
            "MONTHLY" -> ChronoUnit.MONTHS
            "YEARLY" -> ChronoUnit.YEARS
            else -> return sequenceOf(start)
        }
        val periods = generateSequence(0L) { it + 1 }.map { start.plus(it * interval, unit) }
        val raw = if (unit == ChronoUnit.WEEKS && !byDay.isNullOrEmpty()) {
            // Chaque semaine de la règle : tous les jours BYDAY, à l'heure de l'événement d'origine.
            periods.flatMap { weekAnchor ->
                val monday = weekAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                byDay.sorted().asSequence().map { monday.with(TemporalAdjusters.nextOrSame(it)) }
            }.filter { !it.isBefore(start) }
        } else {
            periods
        }
        return raw
            .takeWhile { it.toInstant().toEpochMilli() < to && (until == null || !it.isAfter(until)) }
            .let { if (count != null) it.take(count) else it }
            .take(MAX_OCCURRENCES)
    }

    private companion object {
        const val MAX_OCCURRENCES = 5_000
    }
}

/** Lecteur iCal (RFC 5545) minimal : uniquement les VEVENT et les propriétés utiles. */
object IcalParser {

    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(text: String): List<IcalEvent> {
        // Dépliage : une ligne qui commence par une espace ou une tabulation prolonge la précédente.
        val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        val events = mutableListOf<IcalEvent>()
        var props: MutableList<Triple<String, Map<String, String>, String>>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> props = mutableListOf()
                line == "END:VEVENT" -> {
                    props?.let { toEvent(it) }?.let { events += it }
                    props = null
                }
                props != null -> parseLine(line)?.let { props.add(it) }
            }
        }
        return events
    }

    private fun toEvent(props: List<Triple<String, Map<String, String>, String>>): IcalEvent? {
        fun prop(name: String) = props.firstOrNull { it.first == name }
        if (prop("STATUS")?.third.equals("CANCELLED", ignoreCase = true)) return null
        if (prop("TRANSP")?.third.equals("TRANSPARENT", ignoreCase = true)) return null
        val zone = ZoneId.systemDefault()
        val (startAt, allDay) = prop("DTSTART")?.let { parseDateTime(it.third, it.second, zone) } ?: return null
        val end = prop("DTEND")?.let { parseDateTime(it.third, it.second, zone)?.first }
        val duration = when {
            end != null -> Duration.between(startAt, end)
            prop("DURATION") != null -> runCatching { Duration.parse(prop("DURATION")!!.third) }.getOrNull()
            else -> null
        } ?: if (allDay) Duration.ofDays(1) else Duration.ZERO
        val exdates = props.filter { it.first == "EXDATE" }.flatMap { (_, params, value) ->
            value.split(',').mapNotNull { parseDateTime(it, params, zone)?.first?.toInstant()?.toEpochMilli() }
        }.toSet()
        return IcalEvent(
            start = startAt,
            duration = duration,
            title = prop("SUMMARY")?.third?.unescape().orEmpty(),
            allDay = allDay,
            rrule = prop("RRULE")?.third,
            exdates = exdates,
        )
    }

    /** `NOM;PARAM=V;…:valeur` → (NOM, params, valeur). */
    private fun parseLine(line: String): Triple<String, Map<String, String>, String>? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).uppercase() to it.substring(i + 1).trim('"')
        }.toMap()
        return Triple(head[0].uppercase(), params, line.substring(colon + 1))
    }

    /** Date iCal → (instant zoné, journée entière). Gère UTC (`Z`), `TZID=` et `VALUE=DATE`. */
    fun parseDateTime(value: String, params: Map<String, String>, fallback: ZoneId): Pair<ZonedDateTime, Boolean>? {
        val v = value.trim()
        return runCatching {
            if (params["VALUE"] == "DATE" || v.length == 8) {
                LocalDate.parse(v.take(8), date).atStartOfDay(fallback) to true
            } else if (v.endsWith("Z")) {
                LocalDateTime.parse(v.dropLast(1), dateTime).atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(fallback) to false
            } else {
                val zone = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
                LocalDateTime.parse(v, dateTime).atZone(zone).withZoneSameInstant(fallback) to false
            }
        }.getOrNull()
    }

    fun dayOf(code: String): DayOfWeek? = when (code.uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun String.unescape() =
        replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
