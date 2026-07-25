package com.kairos.app.data.remote

/**
 * Configuration d'accès à l'API Google Gemini. La clé est **dynamique** : lue à chaque accès via
 * [keyProvider] (saisie/modifiée dans les réglages, stockage chiffré), sans recréer le service.
 * Clé vide => [isEnabled] false => Gemini désactivé.
 */
class GeminiConfig(
    private val keyProvider: () -> String,
    val model: String = "gemini-2.5-flash",
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    /** Clé courante (lue à chaque appel, jamais mémorisée). */
    val apiKey: String get() = keyProvider().trim()

    val isEnabled: Boolean get() = apiKey.isNotBlank()
}
