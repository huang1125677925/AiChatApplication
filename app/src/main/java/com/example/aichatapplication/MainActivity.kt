package com.example.aichatapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aichatapplication.ui.AppRootScreen
import com.example.aichatapplication.ui.theme.AiChatApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiChatApplicationTheme {
                AppRootScreen()
            }
        }
    }
}
