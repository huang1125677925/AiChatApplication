package com.example.aichatapplication.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aichatapplication.model.UserProfile
import com.example.aichatapplication.ui.data.DataHomeScreen
import com.example.aichatapplication.ui.data.DataNavRoutes
import com.example.aichatapplication.ui.data.EtfBasicListScreen
import com.example.aichatapplication.ui.data.EtfDailyQueryScreen
import com.example.aichatapplication.ui.data.EtfLatestScreen
import com.example.aichatapplication.ui.data.FundFlowScreen
import com.example.aichatapplication.ui.data.HoldingsScreen
import com.example.aichatapplication.ui.data.RiseFallScreen
import com.example.aichatapplication.ui.data.SseMarketScreen
import com.example.aichatapplication.ui.forum.ForumRootScreen
import com.example.aichatapplication.ui.stock.StockRootScreen
import com.example.aichatapplication.viewmodel.ChatViewModel
import com.example.aichatapplication.viewmodel.ForumViewModel
import com.example.aichatapplication.viewmodel.StockDataViewModel

private val tabs = listOf(
    Triple("行情数据", Icons.Filled.ShowChart, 0),
    Triple("个  股", Icons.Filled.PieChart, 1),
    Triple("论  坛", Icons.Filled.Forum, 2),
    Triple("AI 助手", Icons.Filled.Chat, 3)
)

@Composable
fun MainShellScreen(
    stockDataViewModel: StockDataViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    forumViewModel: ForumViewModel = viewModel(),
    username: String?,
    currentUser: UserProfile?,
    userInfoLoading: Boolean,
    onLogout: () -> Unit,
    onRefreshUserInfo: () -> Unit
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val dataNavController = rememberNavController()

    val dataMessage by stockDataViewModel.dataMessage.collectAsState()
    LaunchedEffect(dataMessage) {
        dataMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            stockDataViewModel.consumeDataMessage()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (tab) {
            // ── 行情数据 Tab ────────────────────────────────────────────────────
            0 -> NavHost(
                navController = dataNavController,
                startDestination = DataNavRoutes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable(DataNavRoutes.HOME) {
                    DataHomeScreen(onOpen = { dataNavController.navigate(it) })
                }
                composable(DataNavRoutes.ETF_BASIC) {
                    EtfBasicListScreen(stockDataViewModel) { dataNavController.popBackStack() }
                }
                composable(DataNavRoutes.ETF_LATEST) {
                    EtfLatestScreen(stockDataViewModel) { dataNavController.popBackStack() }
                }
                composable(DataNavRoutes.ETF_DAILY) {
                    EtfDailyQueryScreen(stockDataViewModel) { dataNavController.popBackStack() }
                }
                composable(DataNavRoutes.SSE_OVERVIEW) {
                    SseMarketScreen(stockDataViewModel) { dataNavController.popBackStack() }
                }
                composable(DataNavRoutes.HOLDINGS) {
                    HoldingsScreen(stockDataViewModel) { dataNavController.popBackStack() }
                }
                composable(DataNavRoutes.FUND_FLOW) {
                    FundFlowScreen(stockDataViewModel) { dataNavController.popBackStack() }
                }
                composable(DataNavRoutes.RISE_FALL) {
                    RiseFallScreen(stockDataViewModel) { dataNavController.popBackStack() }
                }
            }

            // ── 个股 Tab ────────────────────────────────────────────────────────
            1 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                StockRootScreen(vm = stockDataViewModel)
            }

            // ── 论坛 Tab ────────────────────────────────────────────────────────
            2 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ForumRootScreen(vm = forumViewModel)
            }

            // ── AI 助手 Tab ─────────────────────────────────────────────────────
            3 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
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
    }
}
