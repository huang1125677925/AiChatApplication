package com.example.aichatapplication.network

import com.example.aichatapplication.model.ApiResponse
import com.example.aichatapplication.model.ConversationMessage
import com.example.aichatapplication.model.ConversationMessageCreateRequest
import com.example.aichatapplication.model.ConversationStreamRequest
import com.example.aichatapplication.model.ConversationSummary
import com.example.aichatapplication.model.SseResponse
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ChatNetworkClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun createConversation(
        token: String,
        title: String? = null,
        model: String = DEFAULT_MODEL
    ): ConversationSummary {
        val normalizedTitle = title?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_TITLE
        val normalizedModel = model.trim().ifEmpty { DEFAULT_MODEL }
        val payloadCandidates = listOf(
            mapOf("title" to normalizedTitle, "model" to normalizedModel),
            mapOf("title" to normalizedTitle),
            mapOf("model" to normalizedModel),
            emptyMap<String, String>()
        )

        var lastError: Throwable? = null
        var data: JsonElement? = null
        for (payload in payloadCandidates) {
            val result = runCatching {
                postJson(
                    url = "$BASE_URL/conversations/",
                    payload = payload,
                    token = token
                )
            }
            if (result.isSuccess) {
                data = result.getOrNull()
                break
            }
            lastError = result.exceptionOrNull()
        }
        if (data == null) {
            throw lastError ?: IllegalStateException("创建会话失败")
        }
        return gson.fromJson(data, ConversationSummary::class.java)
    }

    fun listConversations(token: String, page: Int = 1, pageSize: Int = 20): List<ConversationSummary> {
        val data = getJson(
            url = "$BASE_URL/conversations/?page=$page&page_size=$pageSize",
            token = token
        )
        return extractList(data, "conversations")
    }

    fun deleteConversation(token: String, conversationId: Long) {
        deleteJson(
            url = "$BASE_URL/conversations/$conversationId/",
            token = token
        )
    }

    fun listMessages(
        token: String,
        conversationId: Long,
        cursor: Int = 0,
        pageSize: Int = 50
    ): List<ConversationMessage> {
        val data = getJson(
            url = "$BASE_URL/conversations/$conversationId/messages/?cursor=$cursor&page_size=$pageSize",
            token = token
        )
        return extractList(data, "messages")
    }

    fun createUserMessage(
        token: String,
        conversationId: Long,
        content: String
    ): ConversationMessage {
        val data = postJson(
            url = "$BASE_URL/conversations/$conversationId/messages/",
            payload = ConversationMessageCreateRequest(content = content),
            token = token
        )
        return gson.fromJson(data, ConversationMessage::class.java)
    }

    fun streamConversation(
        token: String,
        conversationId: Long,
        content: String? = null
    ): Flow<SseResponse> = flow {
        val payload = ConversationStreamRequest(content = content)
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/conversations/$conversationId/stream/")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Authorization", "Bearer $token")
            .build()

        Log.d("DBG_ca7fbf", """{"h":"H2,H5","loc":"ChatNetworkClient:streamConversation","msg":"sse_request_start","data":{"url":"${request.url}"}}""")

        val response = sseClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = runCatching { response.body?.string() }.getOrNull()
            val errorMsg = "HTTP ${response.code}${extractReadableError(errorBody)}"
            Log.d("DBG_ca7fbf", """{"h":"H2,H5","loc":"ChatNetworkClient:streamConversation","msg":"sse_http_error","data":{"code":${response.code}}}""")
            emit(SseResponse(type = "error", content = errorMsg))
            response.close()
            return@flow
        }

        Log.d("DBG_ca7fbf", """{"h":"H2,H5","loc":"ChatNetworkClient:streamConversation","msg":"sse_opened","data":{"code":${response.code}}}""")

        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                if (!trimmed.startsWith("data:")) continue
                val jsonStr = trimmed.removePrefix("data:").trim()
                Log.d("DBG_ca7fbf", """{"h":"H3","loc":"ChatNetworkClient:streamConversation","msg":"sse_line","data":{"preview":"${jsonStr.take(100).replace("\"", "'")}"}}""")
                val sseResponse = runCatching {
                    gson.fromJson(jsonStr, SseResponse::class.java)
                }.getOrNull() ?: continue
                emit(sseResponse)
                if (sseResponse.type == "done" || sseResponse.type == "error") break
            }
        } finally {
            Log.d("DBG_ca7fbf", """{"h":"H3","loc":"ChatNetworkClient:streamConversation","msg":"sse_closed"}""")
            reader.close()
            response.close()
        }
    }

    private fun getJson(url: String, token: String): JsonElement {
        var lastError: Throwable? = null
        for (authHeader in buildAuthHeaders(token)) {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", authHeader)
                .build()
            val result = runCatching { executeForData(request) }
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IOException("请求失败")
    }

    private fun postJson(url: String, payload: Any, token: String): JsonElement {
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        var lastError: Throwable? = null
        for (authHeader in buildAuthHeaders(token)) {
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .build()
            val result = runCatching { executeForData(request) }
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IOException("请求失败")
    }

    private fun deleteJson(url: String, token: String) {
        var lastError: Throwable? = null
        for (authHeader in buildAuthHeaders(token)) {
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", authHeader)
                .build()
            val result = runCatching { executeForCompletion(request) }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IOException("请求失败")
    }

    private fun executeForData(request: Request): JsonElement {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                val errorMsg = buildString {
                    append("HTTP ${response.code} ${request.method} ${request.url}")
                    append(extractReadableError(body))
                }
                throw IOException(errorMsg)
            }
            if (body.isNullOrBlank()) throw IOException("Empty response body")
            val responseType = object : TypeToken<ApiResponse<JsonElement>>() {}.type
            val parsed: ApiResponse<JsonElement> = gson.fromJson(body, responseType)
            if (parsed.code != 200 || parsed.data == null) {
                throw IllegalStateException(parsed.message)
            }
            return parsed.data
        }
    }

    private fun executeForCompletion(request: Request) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                val errorMsg = buildString {
                    append("HTTP ${response.code} ${request.method} ${request.url}")
                    append(extractReadableError(body))
                }
                throw IOException(errorMsg)
            }
            if (body.isNullOrBlank()) return
            val responseType = object : TypeToken<ApiResponse<JsonElement>>() {}.type
            val parsed: ApiResponse<JsonElement> = gson.fromJson(body, responseType)
            if (parsed.code != 200) {
                throw IllegalStateException(parsed.message)
            }
        }
    }

    private inline fun <reified T> extractList(data: JsonElement, fallbackKey: String): List<T> {
        val listType = object : TypeToken<List<T>>() {}.type
        if (data.isJsonArray) {
            return gson.fromJson(data, listType)
        }
        if (data.isJsonObject) {
            val obj = data.asJsonObject
            val candidates = listOf("items", "results", fallbackKey)
            candidates.forEach { key ->
                val array = obj.getAsJsonArrayOrNull(key)
                if (array != null) {
                    return gson.fromJson(array, listType)
                }
            }
        }
        return emptyList()
    }

    private fun JsonObject.getAsJsonArrayOrNull(key: String) = if (has(key) && get(key).isJsonArray) getAsJsonArray(key) else null

    private fun buildAuthHeaders(token: String): List<String> {
        return listOf("Bearer $token", "Token $token").distinct()
    }

    private fun extractReadableError(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val htmlTitle = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)?.trim()
        val exceptionValue = Regex("<pre class=\"exception_value\">(.*?)</pre>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(body)?.groupValues?.getOrNull(1)?.trim()
        val text = when {
            !exceptionValue.isNullOrBlank() -> exceptionValue
            !htmlTitle.isNullOrBlank() -> htmlTitle
            else -> body.take(MAX_ERROR_BODY_LENGTH)
        }
        return if (text.isBlank()) "" else ": ${text.take(MAX_ERROR_BODY_LENGTH)}"
    }

    companion object {
        private const val BASE_URL = "https://www.huanguncle.cn/django/api/chat"
        private const val DEFAULT_MODEL = "deepseek-chat"
        private const val DEFAULT_TITLE = "新会话"
        private const val MAX_ERROR_BODY_LENGTH = 4000
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
