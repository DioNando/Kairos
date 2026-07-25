package com.kairos.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Implémentation [LlmService] via Gemini (endpoint `:generateContent`). La clé voyage dans
 * l'en-tête `x-goog-api-key` (jamais dans l'URL). Retry court sur 503.
 */
class GeminiHttpService(
    private val config: GeminiConfig,
    private val client: OkHttpClient = OkHttpClient(),
) : LlmService {

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        retryOnTransient {
            val payload = JSONObject().put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            val request = Request.Builder()
                .url("${config.baseUrl}/models/${config.model}:generateContent")
                .header("x-goog-api-key", config.apiKey)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val body = try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw when (response.code) {
                            401, 403 -> LlmException.Unauthorized(PROVIDER)
                            429 -> LlmException.RateLimited(PROVIDER)
                            else -> LlmException.Http(PROVIDER, response.code)
                        }
                    }
                    text
                }
            } catch (e: IOException) {
                throw LlmException.Network(PROVIDER, e)
            }
            parseText(body)
        }
    }

    /** Extrait `candidates[0].content.parts[*].text`. */
    private fun parseText(body: String): String =
        try {
            val candidates = JSONObject(body).optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) throw LlmException.Empty(PROVIDER)
            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            val builder = StringBuilder()
            for (i in 0 until parts.length()) builder.append(parts.getJSONObject(i).optString("text"))
            builder.toString().ifBlank { throw LlmException.Empty(PROVIDER) }
        } catch (e: LlmException) {
            throw e
        } catch (e: Exception) {
            throw LlmException.Parse(PROVIDER, e)
        }

    private companion object {
        const val PROVIDER = "Gemini"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
