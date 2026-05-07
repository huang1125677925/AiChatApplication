package com.example.aichatapplication.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aichatapplication.viewmodel.AuthViewModel

@Composable
fun AppRootScreen(authViewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authState.message) {
        authState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            authViewModel.consumeMessage()
        }
    }

    if (authState.isLoggedIn) {
        MainShellScreen(
            username = authState.currentUser?.username,
            currentUser = authState.currentUser,
            userInfoLoading = authState.loading,
            onLogout = authViewModel::logout,
            onRefreshUserInfo = authViewModel::refreshUserInfo
        )
    } else {
        AuthScreen(
            loading = authState.loading,
            onLogin = authViewModel::login,
            onRegister = authViewModel::register,
            onResetPassword = authViewModel::resetPassword
        )
    }
}
