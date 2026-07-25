package com.kairos.app.data.remote

import kotlinx.coroutines.delay

/**
 * Erreurs typées d'un service LLM, **identifiées par fournisseur** (Gemini, OpenRouter…) pour que
 * le message remonté à l'utilisateur indique clairement la source du problème.
 */
sealed class LlmException(message: String) : Exception(message) {
    class Unauthorized(provider: String) : LlmException("$provider : clé API invalide ou expirée")
    class RateLimited(provider: String) : LlmException("$provider : limite/quota atteint (429), réessayez plus tard")
    class Http(provider: String, val code: Int) : LlmException("$provider : erreur HTTP $code")
    class Network(provider: String, cause: Throwable) : LlmException("$provider : réseau indisponible (${cause.message})")
    class Parse(provider: String, cause: Throwable) : LlmException("$provider : réponse illisible (${cause.message})")
    class Empty(provider: String) : LlmException("$provider : réponse vide")
}

/** Service de génération de texte (un prompt → un texte). Implémentations : Gemini, OpenRouter. */
interface LlmService {
    suspend fun generate(prompt: String): String
}

/**
 * Réexécute [block] uniquement sur erreur **transitoire** (503 — serveur momentanément occupé).
 * Un 429 (quota/limite) n'est PAS retenté ici : on le laisse remonter pour basculer immédiatement
 * sur le fournisseur suivant (le retry court ne franchirait pas une fenêtre de limite de toute façon).
 */
internal suspend fun <T> retryOnTransient(maxAttempts: Int = 2, block: suspend () -> T): T {
    var last: LlmException.Http? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: LlmException.Http) {
            if (e.code == 503 && attempt < maxAttempts - 1) {
                last = e
                delay(1_500)
            } else {
                throw e
            }
        }
    }
    throw last ?: LlmException.Http("IA", 503)
}
