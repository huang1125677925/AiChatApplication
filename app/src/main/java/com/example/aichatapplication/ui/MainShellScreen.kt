package com.example.aichatapplication.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aichatapplication.model.UserProfile
import com.example.aichatapplication.viewmodel.ChatViewModel

@Composable
fun MainShellScreen(
    chatViewModel: ChatViewModel = viewModel(),
    username: String?,
    currentUser: UserProfile?,
    userInfoLoading: Boolean,
    onLogout: () -> Unit,
    onRefreshUserInfo: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ChatScreen(
            viewModel = chatViewModel,
            username = username,
            currentUser = currentUser,
            userInfoLoading = userInfoLoading,
            onLogout = onLogout,
            onRefreshUserInfo = onRefreshUserInfo
        )
    }
}
