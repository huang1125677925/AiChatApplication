package com.example.aichatapplication.utils

import android.content.Context

class SessionManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        preferences.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = preferences.getString(KEY_TOKEN, null)

    fun clearToken() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    fun saveUsername(username: String) {
        preferences.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? = preferences.getString(KEY_USERNAME, null)

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "user_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
    }
}
