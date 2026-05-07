package com.example.aichatapplication.network

import com.example.aichatapplication.model.ChatRequest
import com.example.aichatapplication.model.SseResponse
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * AI 网络客户端
 * 用于发起 SSE 连接，与大模型对话接口交互。
 */
class AiNetworkClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val eventSourceFactory = EventSources.createFactory(client)

    /**
     * 发起流式对话请求
     * @param request 聊天请求实体
     * @return 返回 SseResponse 的数据流
     */
    fun chatStream(request: ChatRequest): Flow<SseResponse> = callbackFlow {
        // 构建请求体
        val requestBody = gson.toJson(request).toRequestBody("application/json".toMediaType())

        // 构建请求对象
        // TODO: 请将 "http://10.0.2.2:8000/api/ai_service/chat/" 替换为实际的后端接口地址
        // 如果是本地开发并使用模拟器，可使用 "http://10.0.2.2:8000" (针对 Android 模拟器访问宿主机的 localhost)
        val httpRequest = Request.Builder()
            .url("https://www.huanguncle.cn/django/api/ai/chat/") 
            .post(requestBody)
            .addHeader("Accept", "text/event-stream")
            .build()

        // 创建 SSE 监听器
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                super.onEvent(eventSource, id, type, data)
                try {
                    val sseResponse = gson.fromJson(data, SseResponse::class.java)
                    trySendBlocking(sseResponse)
                    if (sseResponse.type == "done") {
                        close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                super.onFailure(eventSource, t, response)
                t?.printStackTrace()
                trySendBlocking(SseResponse(type = "error", content = t?.message ?: "Unknown Error"))
                close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                super.onClosed(eventSource)
                close()
            }
        }

        // 发起请求并建立 SSE 连接
        val eventSource = eventSourceFactory.newEventSource(httpRequest, listener)

        // 在协程取消时关闭 SSE 连接
        awaitClose {
            eventSource.cancel()
        }
    }
}
