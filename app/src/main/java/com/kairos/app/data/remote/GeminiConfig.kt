package com.kairos.app.data.remote

/**
 * Configuration d'accès à l'API Google Gemini. La clé est **dynamique** : lue à chaque accès via
 * [keyProvider] (saisie/modifiée dans les réglages, stockage chiffré), sans recréer le service.
 * Clé vide => [isEnabled] false => Gemini désactivé. Le modèle est lui aussi lu à chaque appel
 * via [modelProvider] (choisi dans les réglages).
 */
class GeminiConfig(
    private val keyProvider: () -> String,
    private val modelProvider: () -> String = { "gemini-flash-latest" },
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    /** Clé courante (lue à chaque appel, jamais mémorisée). */
    val apiKey: String get() = keyProvider().trim()

    /** Modèle courant (identifiant API, sans préfixe `models/`). */
    val model: String get() = modelProvider().trim().removePrefix("models/")

    val isEnabled: Boolean get() = apiKey.isNotBlank()
}
