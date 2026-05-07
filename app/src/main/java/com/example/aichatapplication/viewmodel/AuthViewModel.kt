package com.example.aichatapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichatapplication.model.LoginRequest
import com.example.aichatapplication.model.RegisterRequest
import com.example.aichatapplication.model.ResetPasswordRequest
import com.example.aichatapplication.model.UserProfile
import com.example.aichatapplication.network.UserNetworkClient
import com.example.aichatapplication.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val loading: Boolean = false,
    val currentUser: UserProfile? = null,
    val message: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val userNetworkClient = UserNetworkClient()
    private val sessionManager = SessionManager(application)

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = !sessionManager.getToken().isNullOrBlank()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (_uiState.value.isLoggedIn) {
            refreshUserInfo()
        }
    }

    fun login(username: String, password: String) {
        val trimmedUser = username.trim()
        if (trimmedUser.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(message = "用户名和密码不能为空") }
            return
        }
        request(
            call = {
                val response = userNetworkClient.login(
                    LoginRequest(username = trimmedUser, password = password)
                )
                val profile = response.data ?: throw IllegalStateException("登录响应缺少用户信息")
                val token = profile.token ?: throw IllegalStateException("登录响应缺少 token")
                sessionManager.saveToken(token)
                sessionManager.saveUsername(profile.username)
                profile
            },
            onSuccess = { profile ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        loading = false,
                        currentUser = profile,
                        message = "登录成功"
                    )
                }
            }
        )
    }

    fun register(
        username: String,
        password: String,
        email: String,
        phone: String,
        invitationCode: String
    ) {
        val trimmedInvite = invitationCode.trim()
        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            _uiState.update { it.copy(message = "用户名、密码和邮箱不能为空") }
            return
        }
        if (trimmedInvite.isBlank()) {
            _uiState.update { it.copy(message = "邀请码不能为空") }
            return
        }
        request(
            call = {
                userNetworkClient.validateInvitation(trimmedInvite)
                userNetworkClient.register(
                    RegisterRequest(
                        username = username,
                        password = password,
                        email = email,
                        phone = phone.ifBlank { null },
                        invitationCode = trimmedInvite
                    )
                )
            },
            onSuccess = {
                _uiState.update { state -> state.copy(loading = false, message = "注册成功，请登录") }
            }
        )
    }

    fun resetPassword(email: String, newPassword: String) {
        if (email.isBlank() || newPassword.isBlank()) {
            _uiState.update { it.copy(message = "邮箱和新密码不能为空") }
            return
        }
        request(
            call = {
                userNetworkClient.resetPassword(
                    ResetPasswordRequest(
                        email = email,
                        newPassword = newPassword
                    )
                )
            },
            onSuccess = {
                _uiState.update { state -> state.copy(loading = false, message = "密码重置成功，请使用新密码登录") }
            }
        )
    }

    fun refreshUserInfo() {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(isLoggedIn = false, currentUser = null) }
            return
        }
        request(
            call = {
                val response = userNetworkClient.getUserInfo(token)
                response.data ?: throw IllegalStateException("用户信息为空")
            },
            onSuccess = { profile ->
                sessionManager.saveUsername(profile.username)
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        loading = false,
                        currentUser = profile,
                        message = null
                    )
                }
            },
            onError = {
                sessionManager.clearAll()
                _uiState.update { state ->
                    state.copy(isLoggedIn = false, loading = false, currentUser = null, message = it.message)
                }
            }
        )
    }

    fun logout() {
        val token = sessionManager.getToken()
        request(
            call = {
                if (!token.isNullOrBlank()) {
                    runCatching { userNetworkClient.logout(token) }
                }
                sessionManager.clearAll()
                Unit
            },
            onSuccess = {
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        loading = false,
                        currentUser = null,
                        message = "已退出登录"
                    )
                }
            }
        )
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun <T> request(
        call: () -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit = {
            _uiState.update { state -> state.copy(loading = false, message = it.message ?: "请求失败") }
        }
    ) {
        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { call() }
                .onSuccess { result -> onSuccess(result) }
                .onFailure { throwable -> onError(throwable) }
        }
    }
}
