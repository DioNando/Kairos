package com.kairos.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stockage **chiffré** des clés API IA saisies dans l'app (Gemini, OpenRouter). Une clé API donne un
 * accès facturable au compte de l'utilisateur : elle ne doit jamais être stockée en clair (le
 * DataStore des réglages l'est). On passe par `EncryptedSharedPreferences` (clé maître dans l'Android
 * Keystore, valeurs chiffrées AES-256). Ces clés sont **exclues des sauvegardes JSON**.
 */
class SecureCredentialStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Clé API Gemini saisie par l'utilisateur, ou `null` si aucune. */
    val geminiKey: String? get() = key(KEY_GEMINI)

    /** Clé API OpenRouter saisie par l'utilisateur, ou `null` si aucune. */
    val openRouterKey: String? get() = key(KEY_OPENROUTER)

    fun setGeminiKey(value: String?) = setKey(KEY_GEMINI, value)

    fun setOpenRouterKey(value: String?) = setKey(KEY_OPENROUTER, value)

    private fun key(name: String): String? = prefs.getString(name, null)?.ifBlank { null }

    private fun setKey(name: String, value: String?) {
        prefs.edit().apply {
            val trimmed = value?.trim()
            if (trimmed.isNullOrBlank()) remove(name) else putString(name, trimmed)
        }.apply()
    }

    private companion object {
        const val FILE_NAME = "kairos_secure_credentials"
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_OPENROUTER = "openrouter_api_key"
    }
}
