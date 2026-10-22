package com.kairos.app.domain.ai

import com.kairos.app.data.model.LlmProvider
import com.kairos.app.data.remote.LlmService

/** Résultat d'une reformulation : titre + note + libellés de sous-tâches remis au propre. */
data class EnhancedTask(
    val title: String,
    val note: String,
    val subtasks: List<String>,
)

/** Classement suggéré par l'IA sur les deux axes de la matrice d'Eisenhower. */
data class Classification(val important: Boolean, val urgent: Boolean)

/**
 * Met au propre / reformule une **tâche entière** (titre + note + sous-tâches) via l'IA, en un seul
 * appel. Isolé derrière une interface pour un repli gracieux (si aucun fournisseur n'est configuré,
 * l'UI garde l'original).
 */
interface NoteEnhancer {
    /** true si au moins un fournisseur IA est configuré (clé présente). */
    val isEnabled: Boolean

    /** Renvoie titre/note/sous-tâches reformulés, ou un [Result.failure] (l'UI garde l'original). */
    suspend fun enhance(title: String, note: String, subtasks: List<String>): Result<EnhancedTask>

    /** Propose un classement (important / urgent) à partir du titre et de la note. */
    suspend fun suggestClassification(title: String, note: String): Result<Classification>

    /** Propose un découpage de la tâche en sous-tâches actionnables. */
    suspend fun suggestSubtasks(title: String, note: String): Result<List<String>>
}

/** Reformule via un [LlmService] unique (Gemini ou OpenRouter). */
class LlmNoteEnhancer(private val service: LlmService) {
    /** Vérifie que le fournisseur répond (clé valide + réseau) via un appel minimal. */
    suspend fun testConnection(): Result<Unit> =
        try {
            service.generate("Réponds uniquement par : OK")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun enhance(title: String, note: String, subtasks: List<String>): Result<EnhancedTask> =
        try {
            val raw = service.generate(taskPrompt(title, note, subtasks)).trim()
            Result.success(parseEnhanced(raw, fallbackTitle = title, fallbackSubtasks = subtasks))
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun suggestClassification(title: String, note: String): Result<Classification> =
        try {
            Result.success(parseClassification(service.generate(classifyPrompt(title, note)).trim()))
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun suggestSubtasks(title: String, note: String): Result<List<String>> =
        try {
            val list = parseSubtaskList(service.generate(subtasksPrompt(title, note)).trim())
            if (list.isEmpty()) Result.failure(IllegalStateException("Aucune sous-tâche générée"))
            else Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
}

/**
 * Reformule via une **chaîne de fournisseurs résolue dynamiquement** : [order] renvoie, à chaque
 * appel, les fournisseurs **configurés** (clé présente) dans l'ordre voulu — primaire choisi par
 * l'utilisateur en tête, repli ensuite. On tente chacun jusqu'au premier succès ; si tous échouent,
 * on renvoie la dernière erreur.
 */
class DynamicNoteEnhancer(
    private val enhancers: Map<LlmProvider, LlmNoteEnhancer>,
    private val order: () -> List<LlmProvider>,
) : NoteEnhancer {

    override val isEnabled: Boolean get() = order().isNotEmpty()

    /** Tente [op] sur chaque fournisseur configuré jusqu'au premier succès (sinon dernière erreur). */
    private suspend fun <T> onChain(op: suspend (LlmNoteEnhancer) -> Result<T>): Result<T> {
        val chain = order().mapNotNull { enhancers[it] }
        if (chain.isEmpty()) return Result.failure(IllegalStateException("Aucun service IA configuré"))
        var last: Result<T> = Result.failure(IllegalStateException("IA indisponible"))
        for (enhancer in chain) {
            val result = op(enhancer)
            if (result.isSuccess) return result
            last = result
        }
        return last
    }

    override suspend fun enhance(title: String, note: String, subtasks: List<String>): Result<EnhancedTask> =
        onChain { it.enhance(title, note, subtasks) }

    override suspend fun suggestClassification(title: String, note: String): Result<Classification> =
        onChain { it.suggestClassification(title, note) }

    override suspend fun suggestSubtasks(title: String, note: String): Result<List<String>> =
        onChain { it.suggestSubtasks(title, note) }
}

private const val TITLE_MARK = "###TITRE###"
private const val NOTE_MARK = "###NOTE###"
private const val SUBTASKS_MARK = "###SOUSTACHES###"

/**
 * Analyse la réponse au format à marqueurs. Repli gracieux : si `###NOTE###` est absent, toute la
 * réponse devient la note (titre/sous-tâches inchangés). Les sous-tâches sont lues ligne par ligne
 * après `###SOUSTACHES###` (puces `- `/`* `/numérotation retirées). Titre/sous-tâches vides retombent
 * sur les valeurs fournies.
 */
internal fun parseEnhanced(
    raw: String,
    fallbackTitle: String,
    fallbackSubtasks: List<String>,
): EnhancedTask {
    val noteIdx = raw.indexOf(NOTE_MARK, ignoreCase = true)
    if (noteIdx < 0) return EnhancedTask(fallbackTitle, raw.trim(), fallbackSubtasks)

    // Titre : région avant ###NOTE###.
    val before = raw.substring(0, noteIdx)
    val titleIdx = before.indexOf(TITLE_MARK, ignoreCase = true)
    val titleRegion = if (titleIdx >= 0) before.substring(titleIdx + TITLE_MARK.length) else before
    val title = titleRegion.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        ?: fallbackTitle

    // Note : région entre ###NOTE### et ###SOUSTACHES### (ou fin).
    val afterNote = raw.substring(noteIdx + NOTE_MARK.length)
    val subIdx = afterNote.indexOf(SUBTASKS_MARK, ignoreCase = true)
    val note = (if (subIdx >= 0) afterNote.substring(0, subIdx) else afterNote).trim()

    // Sous-tâches : lignes après ###SOUSTACHES###, puces retirées, vides ignorées.
    val subtasks = if (subIdx >= 0) {
        afterNote.substring(subIdx + SUBTASKS_MARK.length)
            .lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .map { it.replaceFirst(Regex("^\\d+[.)]\\s+"), "") } // retire une numérotation « 1. » en tête
            .filter { it.isNotEmpty() }
            .toList()
    } else {
        fallbackSubtasks
    }

    return EnhancedTask(
        title = title.ifBlank { fallbackTitle },
        note = note,
        subtasks = subtasks.ifEmpty { fallbackSubtasks },
    )
}

/** Prompt de mise au propre d'une tâche entière (titre + note + sous-tâches), format à marqueurs. */
fun taskPrompt(title: String, note: String, subtasks: List<String>): String {
    val subtasksBlock = if (subtasks.isEmpty()) "(aucune)" else subtasks.joinToString("\n") { "- $it" }
    return """
    Tu mets au propre une tâche (application de gestion de tâches, matrice d'Eisenhower).
    Reformule le TITRE, la NOTE et les SOUS-TÂCHES ci-dessous : corrige l'orthographe et la grammaire,
    rends le titre court, clair et orienté action, structure la note de façon lisible (points clés,
    listes si pertinent), et reformule chaque sous-tâche en libellé court et actionnable. Ton neutre
    et concis.
    Règles strictes :
    - N'invente aucune information et n'ajoute aucun élément absent.
    - Ne supprime aucune information de la note.
    - Conserve **exactement le même nombre de sous-tâches**, dans le même ordre (une par ligne).
    - Conserve à l'identique les liens, les chiffres, les dates et les noms propres.
    - Si la note est vide, renvoie une NOTE vide ; s'il n'y a aucune sous-tâche, laisse la section vide.
    - Réponds EXACTEMENT dans ce format, sans aucun autre texte ni commentaire :
    $TITLE_MARK
    <titre reformulé sur une seule ligne>
    $NOTE_MARK
    <note reformulée>
    $SUBTASKS_MARK
    <une sous-tâche reformulée par ligne, préfixée de « - »>

    TITRE : $title
    NOTE : ${note.ifBlank { "(vide)" }}
    SOUS-TÂCHES :
    $subtasksBlock
    """.trimIndent()
}

// --- Classement (important / urgent) ----------------------------------------------------------

/** Lit un axe booléen (« IMPORTANT: oui ») dans la réponse ; absent → false. */
internal fun parseClassification(raw: String): Classification =
    Classification(important = readAxis(raw, "important"), urgent = readAxis(raw, "urgent"))

private fun readAxis(raw: String, key: String): Boolean {
    val match = Regex("$key\\s*[:=]?\\s*(oui|non|yes|no|true|false|1|0)", RegexOption.IGNORE_CASE).find(raw)
        ?: return false
    return match.groupValues[1].lowercase() in setOf("oui", "yes", "true", "1")
}

fun classifyPrompt(title: String, note: String): String = """
    Tu classes une tâche dans la matrice d'Eisenhower selon deux axes.
    IMPORTANT = la tâche contribue à des objectifs, valeurs ou résultats à moyen/long terme.
    URGENT = il y a une pression de temps réelle ou une échéance proche.
    Juge d'après le contenu, sans rien inventer. Réponds EXACTEMENT dans ce format, sans autre texte :
    IMPORTANT: oui/non
    URGENT: oui/non

    TITRE : $title
    NOTE : ${note.ifBlank { "(vide)" }}
""".trimIndent()

// --- Découpage en sous-tâches ------------------------------------------------------------------

/** Lit une liste de sous-tâches (une par ligne, puces/numérotation retirées). */
internal fun parseSubtaskList(raw: String): List<String> =
    raw.lineSequence()
        .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
        .map { it.replaceFirst(Regex("^\\d+[.)]\\s+"), "") }
        .filter { it.isNotEmpty() }
        .toList()

fun subtasksPrompt(title: String, note: String): String = """
    Découpe la tâche ci-dessous en 3 à 6 sous-tâches concrètes et actionnables, dans l'ordre logique
    de réalisation. Reste strictement dans le périmètre de la tâche, n'invente aucune information.
    Réponds avec une sous-tâche par ligne, préfixée de « - », sans aucun autre texte ni titre.

    TITRE : $title
    NOTE : ${note.ifBlank { "(vide)" }}
""".trimIndent()
