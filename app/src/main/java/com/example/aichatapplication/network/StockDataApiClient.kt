package com.example.aichatapplication.network

import com.example.aichatapplication.model.ApiResponse
import com.example.aichatapplication.model.EtfBasicListPayload
import com.example.aichatapplication.model.EtfDaily
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 股票/ETF/行情等常规数据接口（OpenAPI：/django/api/...），与聊天接口路径分离。
 */
class StockDataApiClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getEtfBasic(
        token: String?,
        page: Int = 1,
        pageSize: Int = 20,
        name: String? = null,
        tsCode: String? = null,
        exchange: String? = null
    ): EtfBasicListPayload {
        val url = buildUrl(
            "/etf/basic/",
            mapOf(
                "page" to page.toString(),
                "page_size" to pageSize.toString(),
                "name" to name,
                "ts_code" to tsCode,
                "exchange" to exchange
            )
        )
        val type = object : TypeToken<EtfBasicListPayload>() {}.type
        return getForData(url, token, type)
    }

    fun getEtfDailyLatest(token: String?): List<EtfDaily> {
        val url = buildUrl("/etf/daily/latest/", emptyMap())
        val listType = object : TypeToken<List<EtfDaily>>() {}.type
        return getForData(url, token, listType)
    }

    fun getEtfDaily(
        token: String?,
        tsCode: String,
        startDate: String? = null,
        endDate: String? = null
    ): List<EtfDaily> {
        val url = buildUrl(
            "/etf/daily/",
            mapOf(
                "ts_code" to tsCode.trim(),
                "start_date" to startDate,
                "end_date" to endDate
            )
        )
        val listType = object : TypeToken<List<EtfDaily>>() {}.type
        return getForData(url, token, listType)
    }

    /** 上证每日概况，结构随后端字段变化，用 JsonElement 展示 */
    fun getSseDailyOverview(token: String?, date: String? = null): JsonElement {
        val url = buildUrl("/market/sse-daily-overview/", mapOf("date" to date))
        return getForData(url, token, JsonElement::class.java)
    }

    fun listHoldings(token: String): JsonElement {
        val url = buildUrl("/personal/holdings/", emptyMap())
        return getForData(url, token, JsonElement::class.java)
    }

    fun addHolding(token: String, stockCode: String, relationType: String = "WATCHED"): JsonElement {
        val url = buildUrl("/personal/holdings/", emptyMap())
        val body = gson.toJson(
            mapOf(
                "stock_code" to stockCode.trim(),
                "relation_type" to relationType
            )
        ).toRequestBody(JSON_MEDIA_TYPE)
        val dataType = object : TypeToken<JsonElement>() {}.type
        return postForData(url, token, body, dataType)
    }

    fun deleteHolding(token: String, holdingId: Int) {
        val url = buildUrl("/personal/holdings/$holdingId/", emptyMap())
        deleteForCompletion(url, token)
    }

    // ── 个股 ────────────────────────────────────────────────────────────────────

    fun getStocks(
        token: String?,
        keyword: String? = null,
        industry: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): JsonElement {
        val url = buildUrl(
            "/individual_stock/stocks/",
            mapOf(
                "keyword" to keyword,
                "industry" to industry,
                "page" to page.toString(),
                "page_size" to pageSize.toString()
            )
        )
        return getRaw(url, token)
    }

    fun getStockInfo(token: String?, stockCode: String): JsonElement =
        getRaw(buildUrl("/individual_stock/stocks/$stockCode/info/", emptyMap()), token)

    fun getStockRealtime(token: String?, stockCode: String): JsonElement =
        getRaw(buildUrl("/individual_stock/stocks/$stockCode/realtime/", emptyMap()), token)

    fun getStockHistory(
        token: String?,
        stockCode: String,
        startDate: String? = null,
        endDate: String? = null,
        adjust: String? = null
    ): JsonElement {
        val url = buildUrl(
            "/individual_stock/stocks/$stockCode/history/",
            mapOf("start_date" to startDate, "end_date" to endDate, "adjust" to adjust)
        )
        return getRaw(url, token)
    }

    // ── 大盘 ────────────────────────────────────────────────────────────────────

    fun getFundFlow(
        token: String?,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): JsonElement {
        val url = buildUrl(
            "/market/fund-flow/",
            mapOf(
                "start_date" to startDate,
                "end_date" to endDate,
                "page" to page.toString(),
                "page_size" to pageSize.toString(),
                "order_by" to "-date"
            )
        )
        return getRaw(url, token)
    }

    fun getRiseFallRatio(
        token: String?,
        indexCode: String? = null,
        limit: Int = 30
    ): JsonElement {
        val url = buildUrl(
            "/market/rise-fall-ratio/",
            mapOf("index_code" to indexCode, "limit" to limit.toString())
        )
        return getRaw(url, token)
    }

    /** 执行 GET 并返回原始 JSON，先尝试 {code,data} 包装，失败则返回顶层 JsonElement */
    private fun getRaw(url: String, token: String?): JsonElement {
        var lastError: Throwable? = null
        for (authHeader in buildAuthHeaders(token)) {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .apply { if (authHeader != null) addHeader("Authorization", authHeader) }
                .build()
            val result = runCatching { executeRaw(request) }
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IOException("请求失败")
    }

    private fun executeRaw(request: Request): JsonElement {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${request.method} ${request.url}${bodyStr?.take(400)?.let { ": $it" } ?: ""}")
            }
            if (bodyStr.isNullOrBlank()) throw IOException("Empty response body")
            val root = gson.fromJson(bodyStr, JsonElement::class.java)
            // Unwrap {code, data} envelope if present
            if (root.isJsonObject) {
                val obj = root.asJsonObject
                if (obj.has("code") && obj.has("data")) {
                    val code = obj.get("code").asInt
                    if (code != 200) {
                        val msg = obj.get("message")?.asString ?: "接口返回错误"
                        throw IllegalStateException(msg)
                    }
                    return obj.get("data") ?: root
                }
            }
            return root
        }
    }

    private fun buildUrl(path: String, query: Map<String, String?>): String {
        val builder = "$BASE_URL$path".toHttpUrl().newBuilder()
        query.forEach { (key, value) ->
            if (!value.isNullOrBlank()) builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun <T> getForData(url: String, token: String?, dataType: java.lang.reflect.Type): T {
        var lastError: Throwable? = null
        for (authHeader in buildAuthHeaders(token)) {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .apply {
                    if (authHeader != null) addHeader("Authorization", authHeader)
                }
                .build()
            val result = runCatching { executeForData<T>(request, dataType) }
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IOException("请求失败")
    }

    private fun <T> postForData(
        url: String,
        token: String,
        body: okhttp3.RequestBody,
        dataType: Type
    ): T {
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        return executeForData<T>(request, dataType)
    }

    private fun deleteForCompletion(url: String, token: String) {
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}${body?.take(500)?.let { ": $it" } ?: ""}")
            }
            if (body.isNullOrBlank()) return
            val responseType = object : TypeToken<ApiResponse<JsonElement>>() {}.type
            val parsed: ApiResponse<JsonElement> = gson.fromJson(body, responseType)
            if (parsed.code != 200) {
                throw IllegalStateException(parsed.message)
            }
        }
    }

    private fun <T> executeForData(request: Request, dataType: java.lang.reflect.Type): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${request.method} ${request.url}${body?.take(400)?.let { ": $it" } ?: ""}")
            }
            if (body.isNullOrBlank()) throw IOException("Empty response body")
            val responseType = TypeToken.getParameterized(ApiResponse::class.java, dataType).type
            @Suppress("UNCHECKED_CAST")
            val parsed: ApiResponse<T> = gson.fromJson(body, responseType)
            if (parsed.code != 200 || parsed.data == null) {
                throw IllegalStateException(parsed.message)
            }
            return parsed.data as T
        }
    }

    private fun buildAuthHeaders(token: String?): List<String?> {
        if (token.isNullOrBlank()) return listOf(null)
        return listOf("Bearer $token", "Token $token").distinct()
    }

    companion object {
        private const val BASE_URL = "https://www.huanguncle.cn/django/api"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
