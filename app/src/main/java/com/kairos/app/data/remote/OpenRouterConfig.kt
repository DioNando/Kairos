package com.kairos.app.data.remote

/**
 * Configuration d'accès à OpenRouter (API compatible OpenAI). Clé **dynamique** lue via [keyProvider]
 * (saisie dans les réglages, stockage chiffré). Clé vide => [isEnabled] false. Sert de repli quand
 * Gemini est indisponible (ou de fournisseur primaire au choix de l'utilisateur).
 */
class OpenRouterConfig(
    private val keyProvider: () -> String,
    // Modèle gratuit OpenRouter (suffixe :free) — correct en français et en suivi d'instructions.
    val model: String = "nex-agi/nex-n2-pro:free",
    val baseUrl: String = "https://openrouter.ai/api/v1",
) {
    /** Clé courante (lue à chaque appel, jamais mémorisée). */
    val apiKey: String get() = keyProvider().trim()

    val isEnabled: Boolean get() = apiKey.isNotBlank()
}
