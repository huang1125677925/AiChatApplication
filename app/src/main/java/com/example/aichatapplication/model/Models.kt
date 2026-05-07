package com.example.aichatapplication.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

/**
 * AI 角色枚举
 */
enum class Role(val value: String) {
    @SerializedName("user") USER("user"),
    @SerializedName("assistant") ASSISTANT("assistant"),
    @SerializedName("tool") TOOL("tool")
}

/**
 * 聊天消息实体
 * @property role 发送者角色
 * @property content 消息内容
 * @property toolCallId 工具调用ID（当role为tool时使用）
 */
data class Message(
    val role: String,
    val content: String,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null
)

/**
 * 聊天请求实体
 * @property messages 历史消息和当前消息列表
 */
data class ChatRequest(
    val messages: List<Message>
)

data class ConversationCreateRequest(
    val title: String? = null,
    val model: String? = null
)

data class ConversationUpdateRequest(
    val title: String? = null,
    @SerializedName("is_pinned")
    val isPinned: Boolean? = null
)

data class ConversationSummary(
    val id: Long,
    val title: String? = null,
    val model: String? = null,
    @SerializedName("is_pinned")
    val isPinned: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

data class ConversationMessage(
    val id: Long,
    @SerializedName("conversation_id")
    val conversationId: Long,
    val role: String,
    val content: String,
    @SerializedName("tool_data")
    val toolData: JsonElement? = null,
    val seq: Int,
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class ConversationMessageCreateRequest(
    val content: String,
    @SerializedName("tool_data")
    val toolData: JsonElement? = null
)

data class ConversationStreamRequest(
    val content: String? = null
)

/**
 * SSE响应流实体
 * @property type 消息类型 (status, tool_card, text, done, error)
 * @property content 文本内容或状态信息或错误信息
 * @property toolName 工具名称
 * @property result 工具执行结果
 * @property args 工具调用参数（JSON 对象）
 */
data class SseResponse(
    val type: String,
    val content: String? = null,
    @SerializedName("tool_name")
    val toolName: String? = null,
    val result: String? = null,
    val args: JsonElement? = null
)

/**
 * UI 层显示的消息模型
 * @property id 唯一标识符
 * @property role 发送者角色
 * @property content 消息文本内容
 * @property isLoading 是否正在加载（通常用于Assistant消息的思考中状态）
 * @property isStreaming 是否处于流式输出中（流式阶段用纯文本，完成后再渲染Markdown）
 * @property isError 是否是错误消息
 * @property toolCards 工具调用卡片列表
 */
data class UiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val toolCards: List<ToolCard> = emptyList()
)

/**
 * 工具卡片模型
 * @property toolName 工具名称
 * @property args 工具调用参数（格式化后的 JSON 文本，无参数时为 null）
 * @property result 工具执行结果
 */
data class ToolCard(
    val toolName: String,
    val args: String? = null,
    val result: String
)

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val phone: String? = null,
    @SerializedName("invitation_code")
    val invitationCode: String? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class ResetPasswordRequest(
    val email: String,
    @SerializedName("new_password")
    val newPassword: String
)

data class InvitationValidateRequest(
    val code: String
)

data class UserProfile(
    val id: Int,
    val username: String,
    val email: String,
    val phone: String? = null,
    @SerializedName("is_admin")
    val isAdmin: Boolean = false,
    @SerializedName("last_login")
    val lastLogin: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    val token: String? = null
)

data class InvitationInfo(
    val id: Int? = null,
    val code: String,
    @SerializedName("is_used")
    val isUsed: Boolean? = null,
    @SerializedName("used_by")
    val usedBy: Int? = null,
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)
