package com.kairos.app.domain.planning

import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task
import com.kairos.app.data.model.TimeBlock

private const val MILLIS_PER_MINUTE = 60_000L

/** Plage de temps [start, end[ (epoch millis). */
data class Interval(val start: Long, val end: Long) {
    val minutes: Int get() = ((end - start) / MILLIS_PER_MINUTE).toInt()

    fun overlaps(other: Interval): Boolean = start < other.end && end > other.start
}

/** Session proposée pour une tâche, en attente de validation. */
data class Placement(val task: Task, val start: Long, val minutes: Int) {
    val interval: Interval get() = Interval(start, start + minutes * MILLIS_PER_MINUTE)
}

/**
 * Calcul d'une journée de planning, sans UI : temps libre, placement au premier créneau qui convient,
 * proposition de remplissage, sessions manquées et temps restant à planifier.
 */
object DayPlan {

    const val SNAP_MINUTES = 15

    /** Nombre maximal de sessions proposées d'un coup par « Remplir ma journée ». */
    const val MAX_PROPOSALS = 4

    /** En deçà, un créneau est trop court pour accueillir un morceau d'une tâche plus longue. */
    private const val MIN_SPLIT_MINUTES = 30

    private const val SNAP_MILLIS = SNAP_MINUTES * MILLIS_PER_MINUTE

    private fun snapUp(millis: Long): Long = -Math.floorDiv(-millis, SNAP_MILLIS) * SNAP_MILLIS

    private fun snapNearest(millis: Long): Long = Math.floorDiv(millis + SNAP_MILLIS / 2, SNAP_MILLIS) * SNAP_MILLIS

    fun interval(block: TimeBlock): Interval = Interval(block.start, block.end)

    /** Temps libre de [window] à partir de [from], calé au quart d'heure, hors plages [occupied]. */
    fun freeSlots(window: Interval, from: Long, occupied: List<Interval>): List<Interval> {
        val slots = mutableListOf<Interval>()
        var cursor = snapUp(maxOf(window.start, from))
        occupied.sortedBy { it.start }.forEach { busy ->
            if (busy.end <= cursor || busy.start >= window.end) return@forEach
            if (busy.start - cursor >= SNAP_MILLIS) slots += Interval(cursor, busy.start)
            cursor = maxOf(cursor, snapUp(busy.end))
        }
        if (window.end - cursor >= SNAP_MILLIS) slots += Interval(cursor, window.end)
        return slots
    }

    /** Début du premier créneau de [slots] pouvant accueillir [minutes], après [after] ; null si aucun. */
    fun firstFit(slots: List<Interval>, minutes: Int, after: Long = Long.MIN_VALUE): Long? {
        val length = minutes * MILLIS_PER_MINUTE
        val earliest = if (after == Long.MIN_VALUE) after else snapUp(after)
        return slots.firstNotNullOfOrNull { slot ->
            maxOf(slot.start, earliest).takeIf { it + length <= slot.end }
        }
    }

    /**
     * Minutes de [task] restant à planifier, [blocks] étant ses sessions. Sans estimation, une seule
     * session suffit ; avec, tant que les sessions ne couvrent pas l'estimation.
     */
    fun remainingMinutes(task: Task, blocks: List<TimeBlock>): Int = when {
        task.estimateMinutes > 0 -> (task.estimateMinutes - blocks.sumOf { it.minutes }).coerceAtLeast(0)
        blocks.isEmpty() -> Task.DEFAULT_BLOCK_MINUTES
        else -> 0
    }

    /**
     * Répartit au plus [MAX_PROPOSALS] sessions de [ranked] (déjà classées par priorité) dans le temps
     * libre. Une tâche trop longue pour tout créneau en occupe le plus grand. Le quadrant Supprimer
     * n'est jamais proposé : ces tâches sont à abandonner, pas à caser.
     */
    fun propose(
        ranked: List<Task>,
        window: Interval,
        from: Long,
        occupied: List<Interval>,
        remaining: (Task) -> Int,
    ): List<Placement> {
        val placements = mutableListOf<Placement>()
        for (task in ranked) {
            if (placements.size == MAX_PROPOSALS) break
            val minutes = remaining(task)
            if (task.quadrant == Quadrant.ELIMINATE || minutes <= 0) continue
            val slots = freeSlots(window, from, occupied + placements.map { it.interval })
            val start = firstFit(slots, minutes)
            if (start != null) {
                placements += Placement(task, start, minutes)
            } else {
                val largest = slots.maxByOrNull { it.minutes }?.takeIf { it.minutes >= MIN_SPLIT_MINUTES } ?: continue
                placements += Placement(task, largest.start, largest.minutes)
            }
        }
        return placements
    }

    /** Sessions passées et non terminées de tâches encore actives, les plus anciennes d'abord. */
    fun missed(blocks: List<TimeBlock>, tasksById: Map<Long, Task>, now: Long): List<TimeBlock> =
        blocks.filter { !it.isDone && it.end <= now && tasksById[it.taskId]?.isActive == true }
            .sortedBy { it.start }

    /** Prochaine session à venir ou en cours, par id de tâche. */
    fun nextBlocks(blocks: List<TimeBlock>, now: Long): Map<Long, TimeBlock> =
        blocks.filter { !it.isDone && it.end > now }
            .groupBy { it.taskId }
            .mapValues { (_, sessions) -> sessions.minBy { it.start } }

    /** Ids des sessions qui chevauchent un rendez-vous ou une autre session. */
    fun conflicts(blocks: List<TimeBlock>, busy: List<Interval>): Set<Long> =
        blocks.filter { block ->
            val range = interval(block)
            busy.any(range::overlaps) || blocks.any { it.id != block.id && range.overlaps(interval(it)) }
        }.mapTo(mutableSetOf()) { it.id }

    /**
     * Terminer la session [blockId] termine-t-il aussi la tâche ? Oui s'il ne reste aucune session à
     * faire et que l'estimation éventuelle est couverte.
     */
    fun finishesTask(task: Task, blocks: List<TimeBlock>, blockId: Long): Boolean =
        blocks.none { it.id != blockId && !it.isDone } && remainingMinutes(task, blocks) == 0

    /**
     * Début d'une session de [minutes] déposée autour de [target], calé au quart d'heure et ramené dans
     * [[earliest], [latestEnd]] ; null si la durée n'y tient pas (journée passée).
     */
    fun snapDrop(target: Long, minutes: Int, earliest: Long, latestEnd: Long): Long? {
        val first = snapUp(earliest)
        val last = latestEnd - minutes * MILLIS_PER_MINUTE
        return if (first > last) null else snapNearest(target).coerceIn(first, last)
    }
}
