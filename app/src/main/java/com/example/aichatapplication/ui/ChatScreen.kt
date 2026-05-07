package com.example.aichatapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.DrawerValue
import androidx.compose.material.ModalDrawer
import androidx.compose.material.rememberDrawerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.aichatapplication.utils.ImageUtils
import com.mikepenz.markdown.m3.Markdown
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.aichatapplication.model.Role
import com.example.aichatapplication.model.ConversationSummary
import com.example.aichatapplication.model.UiMessage
import com.example.aichatapplication.model.UserProfile
import com.example.aichatapplication.viewmodel.ChatViewModel
import kotlin.text.Regex

/**
 * 聊天主页面
 * 单一职责：只负责聊天界面的展示，包括顶栏、消息列表、底部输入框
 * @param viewModel ChatViewModel，管理聊天状态和网络请求
 * @return 返回无
 * @event 通过 viewModel 触发各种事件，如发送消息事件、输入框变更事件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    username: String? = null,
    currentUser: UserProfile? = null,
    userInfoLoading: Boolean = false,
    onLogout: () -> Unit = {},
    onRefreshUserInfo: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isChatting by viewModel.isChatting.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val loadingHistory by viewModel.loadingHistory.collectAsState()
    val message by viewModel.message.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showUserInfoDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var paused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    paused = true
                    viewModel.onAppPaused()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (paused) {
                        paused = false
                        viewModel.onAppResumed()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!message.isNullOrBlank()) {
        ErrorMessageDialog(
            message = message!!,
            onDismiss = { viewModel.consumeMessage() },
            onCopy = {
                clipboardManager.setText(AnnotatedString(message!!))
                android.widget.Toast.makeText(context, "错误信息已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    ModalDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            AppDrawerContent(
                username = username,
                conversations = conversations,
                currentConversationId = currentConversationId,
                onConversationSelected = { conversationId ->
                    viewModel.selectConversation(conversationId)
                    coroutineScope.launch { drawerState.close() }
                },
                onCreateConversation = {
                    viewModel.createConversation()
                    coroutineScope.launch { drawerState.close() }
                },
                onDeleteConversation = { conversationId ->
                    viewModel.deleteConversation(conversationId)
                },
                onItemClick = { item ->
                    coroutineScope.launch {
                        drawerState.close()
                    }
                    when (item) {
                        DrawerItemAction.LOGOUT -> onLogout()
                        DrawerItemAction.USER_INFO -> {
                            showUserInfoDialog = true
                            onRefreshUserInfo()
                        }
                        DrawerItemAction.REFRESH_CONVERSATIONS -> viewModel.refreshConversations()
                        else -> android.widget.Toast.makeText(context, "功能开发中：${item.title}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI 智能助手") },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "打开功能栏")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText = inputText,
                    isChatting = isChatting,
                    onTextChanged = viewModel::onInputTextChanged,
                    onSendClicked = viewModel::sendMessage
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                MessageList(
                    messages = messages,
                    loading = loadingHistory,
                    onRetryMessage = { messageId -> viewModel.retryMessage(messageId) }
                )
            }
        }
    }

    if (showUserInfoDialog) {
        UserInfoDialog(
            currentUser = currentUser,
            loading = userInfoLoading,
            onDismiss = { showUserInfoDialog = false }
        )
    }
}

/**
 * 错误信息弹窗组件
 * 单一职责：展示完整错误内容并提供复制能力
 * @param message 错误文本内容
 * @param onDismiss 关闭弹窗事件
 * @param onCopy 复制错误文本事件
 * @return 返回无
 * @event onDismiss() 点击关闭或取消触发
 * @event onCopy() 点击复制触发
 */
@Composable
private fun ErrorMessageDialog(
    message: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "请求失败") },
        text = {
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text("复制")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun UserInfoDialog(
    currentUser: UserProfile?,
    loading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "用户信息") },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (currentUser == null) {
                        Text(text = "暂无用户信息，请稍后重试")
                    } else {
                        Text(text = "用户ID：${currentUser.id}")
                        Text(text = "用户名：${currentUser.username}")
                        Text(text = "邮箱：${currentUser.email}")
                        Text(text = "手机号：${currentUser.phone ?: "未设置"}")
                        Text(text = "管理员：${if (currentUser.isAdmin) "是" else "否"}")
                        Text(text = "最近登录：${currentUser.lastLogin ?: "暂无"}")
                        Text(text = "注册时间：${currentUser.createdAt ?: "暂无"}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private enum class DrawerItemAction(val title: String) {
    REFRESH_CONVERSATIONS("刷新会话"),
    USER_INFO("用户信息"),
    LOGOUT("退出登录")
}

private enum class DrawerTab(val title: String) {
    CONVERSATIONS("会话"),
    ACCOUNT("账号")
}

@Composable
private fun AppDrawerContent(
    username: String?,
    conversations: List<ConversationSummary>,
    currentConversationId: Long?,
    onConversationSelected: (Long) -> Unit,
    onCreateConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onItemClick: (DrawerItemAction) -> Unit
) {
    val conversationActions = listOf(
        DrawerItemAction.REFRESH_CONVERSATIONS to Icons.Default.Tune
    )
    val accountActions = listOf(
        DrawerItemAction.USER_INFO to Icons.Default.Person,
        DrawerItemAction.LOGOUT to Icons.Default.ExitToApp
    )
    var selectedTab by remember { mutableStateOf(DrawerTab.CONVERSATIONS) }
    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                text = "ChatAir",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = username ?: "未登录用户",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        TabRow(selectedTabIndex = if (selectedTab == DrawerTab.CONVERSATIONS) 0 else 1) {
            DrawerTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = index == if (selectedTab == DrawerTab.CONVERSATIONS) 0 else 1,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) }
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (selectedTab == DrawerTab.CONVERSATIONS) {
                item {
                    NavigationDrawerItem(
                        label = { Text("新建会话") },
                        selected = false,
                        onClick = onCreateConversation,
                        icon = { Icon(imageVector = Icons.Default.Add, contentDescription = "新建会话") }
                    )
                }
                items(conversations) { conversation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavigationDrawerItem(
                            modifier = Modifier.fillMaxWidth(1f),
                            label = { Text(conversation.title ?: "未命名会话 #${conversation.id}") },
                            selected = currentConversationId == conversation.id,
                            onClick = { onConversationSelected(conversation.id) },
                            icon = { Icon(imageVector = Icons.Default.Chat, contentDescription = "会话") }
                        )
                        IconButton(onClick = { onDeleteConversation(conversation.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "删除会话")
                        }
                    }
                }
                items(
                    items = conversationActions,
                    key = { it.first }
                ) { actionPair ->
                    val (item, icon) = actionPair
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        selected = false,
                        onClick = { onItemClick(item) },
                        icon = { Icon(imageVector = icon, contentDescription = item.title) }
                    )
                }
            } else {
                items(
                    items = accountActions,
                    key = { it.first }
                ) { actionPair ->
                    val (item, icon) = actionPair
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        selected = false,
                        onClick = { onItemClick(item) },
                        icon = { Icon(imageVector = icon, contentDescription = item.title) }
                    )
                }
            }
            // 免责声明
            item(key = "disclaimer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "免责声明",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "本项目（Stock Tushare AI App）仅供学习、研究和技术交流使用。项目所提供的所有数据、AI 分析结果及其他相关信息，均不构成任何投资建议。\n\n用户在使用本项目过程中所做出的任何投资决策，均由用户自行承担风险。作者及贡献者对因使用本项目而造成的任何直接或间接的财务损失，不承担任何法律责任。股票市场有风险，投资需谨慎。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 消息列表组件
 * 负责渲染对话消息，包括用户消息和 AI 消息
 * @param messages 消息实体列表
 * @return 返回无
 * @event 无直接事件暴露
 */
@Composable
fun MessageList(
    messages: List<UiMessage>,
    loading: Boolean = false,
    onRetryMessage: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (loading && messages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                items(
                    items = messages.asReversed(),
                    key = { it.id }
                ) { message ->
                    MessageItem(message = message, onRetryMessage = onRetryMessage)
                }
            }
            LazyListScrollbar(
                state = listState,
                reverseScrolling = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * 聊天列表右侧滚动条
 */
@Composable
private fun LazyListScrollbar(
    state: LazyListState,
    reverseScrolling: Boolean = false,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 4.dp,
    minThumbHeight: Dp = 48.dp
) {
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val isScrolling = state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 0 else 800),
        label = "scrollbar_alpha"
    )

    val firstIndex = state.firstVisibleItemIndex
    val firstOffset = state.firstVisibleItemScrollOffset
    val layoutInfo = state.layoutInfo

    val density = LocalDensity.current
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val minThumbPx = with(density) { minThumbHeight.toPx() }

    Canvas(
        modifier = modifier
            .width(thumbWidth + 4.dp)
            .alpha(alpha)
    ) {
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo
        if (totalItems == 0 || visibleItems.isEmpty()) return@Canvas

        val avgSize = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
        val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        val estimatedTotal = (totalItems * avgSize).coerceAtLeast(viewport + 1f)
        if (estimatedTotal <= viewport) return@Canvas

        val thumbH = (viewport / estimatedTotal * viewport).coerceAtLeast(minThumbPx)
        val scrolled = firstIndex * avgSize + firstOffset
        val maxScroll = (estimatedTotal - viewport).coerceAtLeast(1f)
        val fraction = (scrolled / maxScroll).coerceIn(0f, 1f)
        val adjusted = if (reverseScrolling) 1f - fraction else fraction
        val thumbY = (adjusted * (viewport - thumbH)).coerceIn(0f, (viewport - thumbH).coerceAtLeast(0f))

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(size.width - thumbWidthPx, thumbY),
            size = Size(thumbWidthPx, thumbH),
            cornerRadius = CornerRadius(thumbWidthPx / 2, thumbWidthPx / 2)
        )
    }
}

/**
 * 单条消息展示组件
 * 根据发送者角色 (USER / ASSISTANT) 渲染不同的样式
 * @param message 单条消息数据
 * @param onRetryMessage 点击重试回调
 * @return 返回无
 * @event 无直接事件暴露
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: UiMessage,
    onRetryMessage: (String) -> Unit = {}
) {
    val isUser = message.role == Role.USER
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(32.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "AI", color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (message.content.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(message.content))
                                android.widget.Toast
                                    .makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    )
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isUser) {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        if (message.isLoading) {
                            // 加载阶段：显示状态提示文字 + 动态省略号
                            StreamingStatusText(statusText = message.content)
                        } else {
                            SelectionContainer {
                                if (message.isStreaming) {
                                    Text(
                                        text = message.content,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    AssistantMarkdown(content = message.content)
                                }
                            }
                        }
                    }

                    // 渲染工具执行卡片
                    if (message.toolCards.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        message.toolCards.forEach { card ->
                            ToolCardItem(card = card)
                        }
                    }

                    // 如果不是用户发送的消息，并且内容不为空，或者有工具卡片，展示操作按钮
                    if (!isUser && (!message.isLoading || message.content.isNotEmpty() || message.toolCards.isNotEmpty())) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (message.isError) {
                                // 重试按钮
                                Card(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable { onRetryMessage(message.id) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "重试",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            // 复制按钮
                            Card(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                        android.widget.Toast.makeText(context, "已复制结果", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "复制",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            // 导出图片按钮
                            Card(
                                modifier = Modifier
                                    .clickable {
                                        coroutineScope.launch {
                                            try {
                                                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                                ImageUtils.saveBitmapToGallery(context, bitmap)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "导出图片失败", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "导出图片",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            // User 头像占位
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(32.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "U", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/**
 * 工具卡片组件，支持折叠；标题栏单击切换展开，双击收起。
 */
/**
 * 流式加载状态文字，带动态省略号动画，用于展示"正在准备回复..."等 status 事件内容。
 */
@Composable
private fun StreamingStatusText(statusText: String) {
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(statusText) {
        while (true) {
            delay(400)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }
    val displayText = if (statusText.isBlank()) "思考中" else statusText.trimEnd('.')
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier
                .width(14.dp)
                .height(14.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 1.5.dp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = displayText + ".".repeat(dotCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCardItem(card: com.example.aichatapplication.model.ToolCard) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Column {
            // Header: 单击展开/折叠，双击收起
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onDoubleClick = { expanded = false }
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "工具: ${card.toolName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Content: 展开时显示结果，双击内容区域也可收起
            if (expanded) {
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = { expanded = false }
                        )
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    if (!card.args.isNullOrBlank()) {
                        Text(
                            text = "参数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = card.args,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "结果",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = card.result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun shrinkMarkdownHeadings(content: String): String {
    val headingRegex = Regex("^(#{1,6})\\s+", RegexOption.MULTILINE)
    return headingRegex.replace(content) { matchResult ->
        val currentLevel = matchResult.groupValues[1].length
        val targetLevel = (currentLevel + 2).coerceAtMost(6)
        "#".repeat(targetLevel) + " "
    }
}

@Composable
private fun AssistantMarkdown(content: String) {
    val segments = remember(content) { parseMarkdownSegments(content) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownTextSegment -> {
                    if (segment.text.isNotBlank()) {
                        Markdown(content = shrinkMarkdownHeadings(segment.text))
                    }
                }
                is MarkdownTableSegment -> MarkdownTable(table = segment)
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownTableSegment) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.horizontalScroll(scrollState)) {
        Column(
            modifier = Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
        ) {
            MarkdownTableRow(
                cells = table.header,
                isHeader = true
            )
            table.rows.forEach { row ->
                MarkdownTableRow(
                    cells = row,
                    isHeader = false
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(cells: List<String>, isHeader: Boolean) {
    Row {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .widthIn(min = 120.dp)
                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    .background(
                        if (isHeader) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = cell.ifBlank { " " },
                    style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    color = if (isHeader) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private sealed interface MarkdownSegment

private data class MarkdownTextSegment(val text: String) : MarkdownSegment

private data class MarkdownTableSegment(
    val header: List<String>,
    val rows: List<List<String>>
) : MarkdownSegment

private fun parseMarkdownSegments(content: String): List<MarkdownSegment> {
    val lines = content.lines()
    val segments = mutableListOf<MarkdownSegment>()
    val textBuffer = mutableListOf<String>()
    var index = 0

    fun flushTextBuffer() {
        if (textBuffer.isNotEmpty()) {
            val text = textBuffer.joinToString("\n").trim('\n')
            if (text.isNotBlank()) {
                segments.add(MarkdownTextSegment(text))
            }
            textBuffer.clear()
        }
    }

    while (index < lines.size) {
        if (isTableStart(lines, index)) {
            flushTextBuffer()
            val header = parseTableRow(lines[index])
            var rowIndex = index + 2
            val bodyRows = mutableListOf<List<String>>()
            while (rowIndex < lines.size && isTableRow(lines[rowIndex])) {
                bodyRows.add(parseTableRow(lines[rowIndex]))
                rowIndex++
            }

            val columnCount = maxOf(
                header.size,
                bodyRows.maxOfOrNull { it.size } ?: 0
            ).coerceAtLeast(1)

            segments.add(
                MarkdownTableSegment(
                    header = normalizeRow(header, columnCount),
                    rows = bodyRows.map { normalizeRow(it, columnCount) }
                )
            )
            index = rowIndex
        } else {
            textBuffer.add(lines[index])
            index++
        }
    }

    flushTextBuffer()
    return if (segments.isEmpty()) listOf(MarkdownTextSegment(content)) else segments
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    if (!isTableRow(lines[index])) return false
    return isTableSeparator(lines[index + 1])
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank() || !trimmed.contains("|")) return false
    return parseTableRow(trimmed).size >= 2
}

private fun isTableSeparator(line: String): Boolean {
    val separatorRegex = Regex("^\\s*\\|?(\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?\\s*$")
    return separatorRegex.matches(line)
}

private fun parseTableRow(line: String): List<String> {
    var row = line.trim()
    if (row.startsWith("|")) row = row.drop(1)
    if (row.endsWith("|")) row = row.dropLast(1)

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false

    row.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == '|' -> {
                cells.add(current.toString().trim())
                current.clear()
            }
            else -> current.append(char)
        }
    }
    cells.add(current.toString().trim())
    return cells
}

private fun normalizeRow(row: List<String>, targetSize: Int): List<String> {
    if (row.size >= targetSize) return row
    return row + List(targetSize - row.size) { "" }
}

/**
 * 底部输入栏组件
 * 包含文本输入框和发送按钮
 * @param inputText 当前输入的文本内容
 * @param isChatting 是否正在等待 AI 响应
 * @param onTextChanged 文本变更回调
 * @param onSendClicked 发送按钮点击回调
 * @return 返回无
 * @event onTextChanged(String) 当输入框文本改变时触发
 * @event onSendClicked() 当点击发送按钮或软键盘发送键时触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    inputText: String,
    isChatting: Boolean,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = inputText,
            onValueChange = onTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入你的问题...") },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendClicked() }),
            enabled = !isChatting
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onSendClicked,
            enabled = inputText.isNotBlank() && !isChatting,
            modifier = Modifier
                .background(
                    color = if (inputText.isNotBlank() && !isChatting) MaterialTheme.colorScheme.primary else Color.Gray,
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "发送",
                tint = Color.White
            )
        }
    }
}
