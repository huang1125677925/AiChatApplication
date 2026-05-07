package com.example.aichatapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichatapplication.network.ForumApiClient
import com.example.aichatapplication.utils.SessionManager
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForumPost(
    val id: Int,
    val title: String,
    val author: String,
    val content: String? = null,
    val createdAt: String? = null,
    val commentsCount: Int = 0
)

data class ForumComment(
    val id: Int,
    val content: String,
    val author: String,
    val createdAt: String? = null
)

class ForumViewModel(application: Application) : AndroidViewModel(application) {
    private val apiClient = ForumApiClient()
    private val sessionManager = SessionManager(application)

    private val _posts = MutableStateFlow<LoadState<List<ForumPost>>>(LoadState.Idle)
    val posts: StateFlow<LoadState<List<ForumPost>>> = _posts.asStateFlow()

    private val _currentPost = MutableStateFlow<LoadState<Pair<ForumPost, List<ForumComment>>>>(LoadState.Idle)
    val currentPost: StateFlow<LoadState<Pair<ForumPost, List<ForumComment>>>> = _currentPost.asStateFlow()

    private val _forumMessage = MutableStateFlow<String?>(null)
    val forumMessage: StateFlow<String?> = _forumMessage.asStateFlow()

    fun consumeForumMessage() { _forumMessage.value = null }

    fun loadPosts() {
        viewModelScope.launch(Dispatchers.IO) {
            _posts.value = LoadState.Loading
            _posts.value = runCatching { parsePosts(apiClient.getPosts()) }
                .fold(
                    onSuccess = { LoadState.Ok(it) },
                    onFailure = { LoadState.Err(it.message ?: it.toString()) }
                )
        }
    }

    fun loadPost(postId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentPost.value = LoadState.Loading
            _currentPost.value = runCatching { parsePostDetail(apiClient.getPost(postId)) }
                .fold(
                    onSuccess = { LoadState.Ok(it) },
                    onFailure = { LoadState.Err(it.message ?: it.toString()) }
                )
        }
    }

    fun createPost(title: String, content: String) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) { _forumMessage.value = "请先登录"; return }
        if (title.isBlank() || content.isBlank()) { _forumMessage.value = "标题和内容不能为空"; return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { apiClient.createPost(token, title.trim(), content.trim()) }
                .onSuccess { _forumMessage.value = "发帖成功"; loadPosts() }
                .onFailure { _forumMessage.value = it.message ?: "发帖失败" }
        }
    }

    fun addComment(postId: Int, content: String) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) { _forumMessage.value = "请先登录"; return }
        if (content.isBlank()) { _forumMessage.value = "评论内容不能为空"; return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { apiClient.addComment(token, postId, content.trim()) }
                .onSuccess { _forumMessage.value = "评论成功"; loadPost(postId) }
                .onFailure { _forumMessage.value = it.message ?: "评论失败" }
        }
    }

    fun deletePost(postId: Int) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) { _forumMessage.value = "请先登录"; return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { apiClient.deletePost(token, postId) }
                .onSuccess { _forumMessage.value = "已删除"; loadPosts() }
                .onFailure { _forumMessage.value = it.message ?: "删除失败" }
        }
    }

    // ── JSON 解析 ────────────────────────────────────────────────────────────────

    private fun parsePosts(json: JsonElement): List<ForumPost> {
        val arr = unwrapArray(json) ?: return emptyList()
        return arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val id = o.intOrNull("id") ?: return@mapNotNull null
            ForumPost(
                id = id,
                title = o.strOrNull("title") ?: "(无标题)",
                author = extractAuthor(o),
                content = o.strOrNull("content"),
                createdAt = o.strOrNull("created_at"),
                commentsCount = o.intOrNull("comments_count") ?: 0
            )
        }
    }

    private fun parsePostDetail(json: JsonElement): Pair<ForumPost, List<ForumComment>> {
        val obj = unwrapObject(json) ?: throw IllegalStateException("Invalid post detail response")
        val post = ForumPost(
            id = obj.intOrNull("id") ?: 0,
            title = obj.strOrNull("title") ?: "(无标题)",
            author = extractAuthor(obj),
            content = obj.strOrNull("content") ?: "",
            createdAt = obj.strOrNull("created_at")
        )
        val comments = obj.getAsJsonArray("comments")?.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val co = el.asJsonObject
            val cid = co.intOrNull("id") ?: return@mapNotNull null
            ForumComment(
                id = cid,
                content = co.strOrNull("content") ?: "",
                author = extractAuthor(co),
                createdAt = co.strOrNull("created_at")
            )
        } ?: emptyList()
        return Pair(post, comments)
    }

    private fun extractAuthor(obj: JsonObject): String {
        val authorEl = if (obj.has("author")) obj.get("author") else null
        return when {
            authorEl != null && authorEl.isJsonPrimitive -> authorEl.asString
            authorEl != null && authorEl.isJsonObject -> {
                val a = authorEl.asJsonObject
                a.strOrNull("username") ?: a.strOrNull("name") ?: "匿名"
            }
            else -> obj.strOrNull("username") ?: "匿名"
        }
    }

    private fun unwrapArray(json: JsonElement): com.google.gson.JsonArray? {
        if (json.isJsonArray) return json.asJsonArray
        if (!json.isJsonObject) return null
        val obj = json.asJsonObject
        if (obj.has("code") && obj.has("data")) {
            val data = obj.get("data")
            if (data.isJsonArray) return data.asJsonArray
            if (data.isJsonObject) {
                val d = data.asJsonObject
                return listOf("list", "items", "results").firstNotNullOfOrNull { k ->
                    if (d.has(k) && d.get(k).isJsonArray) d.getAsJsonArray(k) else null
                }
            }
        }
        return listOf("list", "items", "results").firstNotNullOfOrNull { k ->
            if (obj.has(k) && obj.get(k).isJsonArray) obj.getAsJsonArray(k) else null
        }
    }

    private fun unwrapObject(json: JsonElement): JsonObject? {
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            return when {
                obj.has("code") && obj.has("data") && obj.get("data").isJsonObject ->
                    obj.getAsJsonObject("data")
                else -> obj
            }
        }
        return null
    }

    private fun JsonObject.strOrNull(key: String): String? =
        runCatching { if (has(key) && !get(key).isJsonNull) get(key).asString else null }.getOrNull()

    private fun JsonObject.intOrNull(key: String): Int? =
        runCatching { if (has(key) && !get(key).isJsonNull) get(key).asInt else null }.getOrNull()
}
