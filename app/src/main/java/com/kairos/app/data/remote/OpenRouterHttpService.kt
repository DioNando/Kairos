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
 * Implémentation [LlmService] via OpenRouter (endpoint `chat/completions`, compatible OpenAI).
 * Retry court sur 503.
 */
class OpenRouterHttpService(
    private val config: OpenRouterConfig,
    private val client: OkHttpClient = OkHttpClient(),
) : LlmService {

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        retryOnTransient {
            val payload = JSONObject()
                .put("model", config.model)
                .put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
                )
            val request = Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("HTTP-Referer", "https://kairos.app")
                .header("X-Title", "Kairos")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val body = try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw when (response.code) {
                            401, 403 -> LlmException.Unauthorized(PROVIDER)
                            429 -> LlmException.RateLimited(PROVIDER)
                            404 -> LlmException.ModelNotFound(PROVIDER)
                            else -> LlmException.Http(PROVIDER, response.code)
                        }
                    }
                    text
                }
            } catch (e: IOException) {
                throw LlmException.Network(PROVIDER, e)
            }
            parseContent(body)
        }
    }

    /** Liste les modèles OpenRouter (les gratuits `:free` en tête, puis ordre alphabétique). */
    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.baseUrl}/models")
            .apply { if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}") }
            .get()
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
        try {
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).map { data.getJSONObject(it).optString("id") }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedWith(compareBy<String>({ !it.endsWith(":free") }, { it }))
        } catch (e: Exception) {
            throw LlmException.Parse(PROVIDER, e)
        }
    }

    /** Extrait `choices[0].message.content`. */
    private fun parseContent(body: String): String =
        try {
            val choices = JSONObject(body).optJSONArray("choices")
            if (choices == null || choices.length() == 0) throw LlmException.Empty(PROVIDER)
            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
            content.ifBlank { throw LlmException.Empty(PROVIDER) }
        } catch (e: LlmException) {
            throw e
        } catch (e: Exception) {
            throw LlmException.Parse(PROVIDER, e)
        }

    private companion object {
        const val PROVIDER = "OpenRouter"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
