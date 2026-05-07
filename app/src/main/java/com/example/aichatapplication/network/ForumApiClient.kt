package com.example.aichatapplication.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ForumApiClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getPosts(): JsonElement = get("$BASE_URL/posts/", null)

    fun getPost(postId: Int): JsonElement = get("$BASE_URL/posts/$postId/", null)

    fun createPost(token: String, title: String, content: String): JsonElement {
        val body = gson.toJson(mapOf("title" to title, "content" to content))
            .toRequestBody(JSON_MEDIA_TYPE)
        return post("$BASE_URL/posts/create/", token, body)
    }

    fun addComment(token: String, postId: Int, content: String): JsonElement {
        val body = gson.toJson(mapOf("content" to content))
            .toRequestBody(JSON_MEDIA_TYPE)
        return post("$BASE_URL/posts/$postId/comment/", token, body)
    }

    fun deletePost(token: String, postId: Int): JsonElement {
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        return post("$BASE_URL/posts/$postId/delete/", token, body)
    }

    private fun get(url: String, token: String?): JsonElement {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
            .build()
        return execute(request)
    }

    private fun post(url: String, token: String?, body: okhttp3.RequestBody): JsonElement {
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
            .build()
        return execute(request)
    }

    private fun execute(request: Request): JsonElement {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${bodyStr?.take(300)}")
            }
            if (bodyStr.isNullOrBlank()) throw IOException("Empty response body")
            return runCatching { gson.fromJson(bodyStr, JsonElement::class.java) }
                .getOrElse { throw IOException("Invalid JSON: ${bodyStr.take(200)}") }
        }
    }

    companion object {
        private const val BASE_URL = "https://www.huanguncle.cn/django/api/forum"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
