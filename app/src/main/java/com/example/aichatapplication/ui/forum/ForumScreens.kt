package com.example.aichatapplication.ui.forum

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aichatapplication.viewmodel.ForumPost
import com.example.aichatapplication.viewmodel.ForumComment
import com.example.aichatapplication.viewmodel.ForumViewModel
import com.example.aichatapplication.viewmodel.LoadState

private object ForumNav {
    const val LIST = "forum_list"
    const val DETAIL = "forum_detail/{postId}"
    const val CREATE = "forum_create"
    fun detail(id: Int) = "forum_detail/$id"
}

@Composable
fun ForumRootScreen(vm: ForumViewModel = viewModel()) {
    val context = LocalContext.current
    val msg by vm.forumMessage.collectAsState()

    LaunchedEffect(msg) {
        msg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeForumMessage()
        }
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ForumNav.LIST) {
        composable(ForumNav.LIST) {
            ForumListScreen(vm = vm, navController = navController)
        }
        composable(ForumNav.DETAIL) { back ->
            val postId = back.arguments?.getString("postId")?.toIntOrNull() ?: return@composable
            PostDetailScreen(vm = vm, postId = postId, navController = navController)
        }
        composable(ForumNav.CREATE) {
            CreatePostScreen(vm = vm, navController = navController)
        }
    }
}

// ── 帖子列表 ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumListScreen(vm: ForumViewModel, navController: NavHostController) {
    val state by vm.posts.collectAsState()
    LaunchedEffect(Unit) { vm.loadPosts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("论坛") },
                actions = {
                    IconButton(onClick = { vm.loadPosts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(ForumNav.CREATE) }) {
                Icon(Icons.Default.Add, contentDescription = "发帖")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                LoadState.Idle, LoadState.Loading -> CenteredProgress()
                is LoadState.Err -> ErrorBlock(s.message)
                is LoadState.Ok -> {
                    if (s.value.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无帖子，点击右下角发帖", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(s.value, key = { it.id }) { post ->
                                PostCard(post) {
                                    navController.navigate(ForumNav.detail(post.id))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: ForumPost, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    post.author,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    post.createdAt?.take(10) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (post.commentsCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${post.commentsCount} 条评论",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── 帖子详情 ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostDetailScreen(vm: ForumViewModel, postId: Int, navController: NavHostController) {
    val state by vm.currentPost.collectAsState()
    var commentInput by rememberSaveable { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(postId) { vm.loadPost(postId) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除帖子") },
            text = { Text("确定删除这篇帖子吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deletePost(postId)
                    navController.popBackStack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帖子详情") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除帖子")
                    }
                    IconButton(onClick = { vm.loadPost(postId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                LoadState.Idle, LoadState.Loading -> CenteredProgress()
                is LoadState.Err -> ErrorBlock(s.message)
                is LoadState.Ok -> {
                    val (post, comments) = s.value
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(post.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(post.createdAt?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!post.content.isNullOrBlank()) {
                            Text(post.content, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(20.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))
                        Text("评论 (${comments.size})", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        if (comments.isEmpty()) {
                            Text("暂无评论", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            comments.forEach { comment ->
                                CommentCard(comment)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    // 评论输入区
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("添加评论") },
                            maxLines = 3
                        )
                        Button(
                            onClick = {
                                vm.addComment(postId, commentInput)
                                commentInput = ""
                            }
                        ) { Text("发送") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentCard(comment: ForumComment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(comment.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(comment.createdAt?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.content, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── 发帖 ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostScreen(vm: ForumViewModel, navController: NavHostController) {
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发帖") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                singleLine = true
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = { Text("正文") },
                maxLines = 12
            )
            Button(
                onClick = { vm.createPost(title, content) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("发布")
            }
        }
    }
}

// ── 工具组件 ─────────────────────────────────────────────────────────────────

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBlock(msg: String) {
    Text(
        msg,
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}
