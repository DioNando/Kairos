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
