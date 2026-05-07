package com.example.aichatapplication.network

import com.example.aichatapplication.model.ApiResponse
import com.example.aichatapplication.model.InvitationInfo
import com.example.aichatapplication.model.InvitationValidateRequest
import com.example.aichatapplication.model.LoginRequest
import com.example.aichatapplication.model.RegisterRequest
import com.example.aichatapplication.model.ResetPasswordRequest
import com.example.aichatapplication.model.UserProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class UserNetworkClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun register(request: RegisterRequest): ApiResponse<UserProfile> {
        return post("$BASE_URL/register/", request, userProfileType)
    }

    fun login(request: LoginRequest): ApiResponse<UserProfile> {
        // 使用 Map 序列化，避免 Gson 对部分 Kotlin data class 反射序列化异常（与 curl/Web 的 JSON 不一致）
        val payload = mapOf(
            "username" to request.username,
            "password" to request.password
        )
        return post("$BASE_URL/login/", payload, userProfileType)
    }

    fun logout(token: String): ApiResponse<Any> {
        return post("$BASE_URL/logout/", emptyMap<String, String>(), emptyType, token)
    }

    fun resetPassword(request: ResetPasswordRequest): ApiResponse<Any> {
        return post("$BASE_URL/reset-password/", request, emptyType)
    }

    fun getUserInfo(token: String): ApiResponse<UserProfile> {
        return get("$BASE_URL/info/", userProfileType, token)
    }

    fun createInvitation(token: String): ApiResponse<InvitationInfo> {
        return post("$BASE_URL/invitation/", emptyMap<String, String>(), invitationType, token)
    }

    fun listInvitations(token: String): ApiResponse<List<InvitationInfo>> {
        return get("$BASE_URL/invitation/", invitationListType, token)
    }

    fun validateInvitation(code: String): ApiResponse<Any> {
        return post(
            "$BASE_URL/invitation/validate/",
            InvitationValidateRequest(code = code),
            emptyType
        )
    }

    private fun <T> post(
        url: String,
        payload: Any,
        dataType: java.lang.reflect.Type,
        token: String? = null
    ): ApiResponse<T> {
        val bodyJson = gson.toJson(payload)
        val body = bodyJson.toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            // 勿手写 Content-Type，否则会覆盖 RequestBody 上的 charset，与浏览器/curl 行为不一致
            .addHeader("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        return execute(requestBuilder.build(), dataType)
    }

    private fun <T> get(
        url: String,
        dataType: java.lang.reflect.Type,
        token: String
    ): ApiResponse<T> {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        return execute(request, dataType)
    }

    private fun <T> execute(request: Request, dataType: java.lang.reflect.Type): ApiResponse<T> {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Empty response body")
            if (!response.isSuccessful) {
                val apiMessage = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (gson.fromJson(body, Map::class.java) as Map<*, *>)["message"] as? String
                }.getOrNull()?.takeIf { it.isNotBlank() }
                throw IOException(apiMessage ?: "HTTP ${response.code}")
            }
            val responseType = TypeToken.getParameterized(ApiResponse::class.java, dataType).type
            val parsed: ApiResponse<T> = gson.fromJson(body, responseType)
            if (parsed.code != 200) {
                throw IllegalStateException(parsed.message)
            }
            return parsed
        }
    }

    companion object {
        private const val BASE_URL = "https://www.huanguncle.cn/django/api/user"
        /** 显式 UTF-8，避免部分服务端对默认编码解析与 Web 不一致 */
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val userProfileType = object : TypeToken<UserProfile>() {}.type
        private val invitationType = object : TypeToken<InvitationInfo>() {}.type
        private val invitationListType = object : TypeToken<List<InvitationInfo>>() {}.type
        private val emptyType = object : TypeToken<Any>() {}.type
    }
}
