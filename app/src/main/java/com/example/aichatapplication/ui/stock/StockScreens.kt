package com.example.aichatapplication.ui.stock

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.aichatapplication.viewmodel.LoadState
import com.example.aichatapplication.viewmodel.StockDataViewModel
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement

/** 个股 Tab 顶层，包含「搜索列表」和「详情查询」两个子 Tab */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockRootScreen(vm: StockDataViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("搜索列表", "行情查询")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = tabIndex == idx,
                    onClick = { tabIndex = idx },
                    text = { Text(title) }
                )
            }
        }
        when (tabIndex) {
            0 -> StockListSubScreen(vm)
            1 -> StockDetailSubScreen(vm)
        }
    }
}

// ── 搜索列表 ─────────────────────────────────────────────────────────────────

@Composable
private fun StockListSubScreen(vm: StockDataViewModel) {
    val state by vm.stocks.collectAsState()
    val keyword by vm.stockKeyword.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = vm::setStockKeyword,
                modifier = Modifier.weight(1f),
                label = { Text("名称 / 代码") },
                singleLine = true,
                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.loadStocks() })
            )
            Button(onClick = { vm.loadStocks() }) { Text("搜索") }
        }
        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            LoadState.Idle -> Text(
                "输入关键字后点击搜索，可以查找股票",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            LoadState.Loading -> CenteredProgress()
            is LoadState.Err -> ErrorText(s.message)
            is LoadState.Ok -> StockListContent(json = s.value, vm = vm)
        }
    }
}

@Composable
private fun StockListContent(json: JsonElement, vm: StockDataViewModel) {
    val rows = remember(json) { vm.jsonToRows(json) }
    if (rows.isEmpty()) {
        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val keyFields = listOf("name", "stock_name", "ts_code", "code", "industry", "area")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows) { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    val name = row["name"] ?: row["stock_name"] ?: row["ts_code"] ?: "(未知)"
                    val code = row["ts_code"] ?: row["code"] ?: ""
                    Text("$name  $code", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    val sub = keyFields.drop(2).mapNotNull { k -> row[k]?.let { "$k: $it" } }.joinToString("  ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── 行情查询（实时 + 历史）────────────────────────────────────────────────────

@Composable
private fun StockDetailSubScreen(vm: StockDataViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var innerTab by rememberSaveable { mutableIntStateOf(0) }
    val detailState by vm.stockDetail.collectAsState()
    val realtimeState by vm.stockRealtime.collectAsState()
    val historyState by vm.stockHistory.collectAsState()

    var histStart by rememberSaveable { mutableStateOf("") }
    var histEnd by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.weight(1f),
                label = { Text("股票代码（如 000001.SZ）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.loadStockDetail(code) })
            )
            Button(onClick = { vm.loadStockDetail(code) }) { Text("查询") }
        }
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = innerTab) {
            listOf("基本信息", "实时行情", "历史走势").forEachIndexed { i, t ->
                Tab(selected = innerTab == i, onClick = { innerTab = i }, text = { Text(t) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (innerTab) {
            0 -> JsonDisplaySection(detailState, emptyLabel = "请输入股票代码后查询")
            1 -> JsonDisplaySection(realtimeState, emptyLabel = "请先查询以获取实时行情") {
                IconButton(onClick = { vm.loadStockDetail(code) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
            2 -> HistorySection(vm, code, histStart, histEnd,
                onStartChange = { histStart = it },
                onEndChange = { histEnd = it },
                historyState = historyState
            )
        }
    }
}

@Composable
private fun JsonDisplaySection(
    state: LoadState<JsonElement>,
    emptyLabel: String,
    actions: @Composable () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            LoadState.Idle -> Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LoadState.Loading -> CenteredProgress()
            is LoadState.Err -> ErrorText(state.message)
            is LoadState.Ok -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    val pretty = remember(state.value) {
                        GsonBuilder().setPrettyPrinting().create().toJson(state.value)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("数据", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        actions()
                    }
                    Text(
                        pretty,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySection(
    vm: StockDataViewModel,
    code: String,
    histStart: String,
    histEnd: String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    historyState: LoadState<JsonElement>
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = histStart,
                onValueChange = onStartChange,
                modifier = Modifier.weight(1f),
                label = { Text("start YYYYMMDD") },
                singleLine = true
            )
            OutlinedTextField(
                value = histEnd,
                onValueChange = onEndChange,
                modifier = Modifier.weight(1f),
                label = { Text("end YYYYMMDD") },
                singleLine = true
            )
        }
        OutlinedButton(onClick = { vm.loadStockHistory(code, histStart, histEnd, null) }) {
            Text("获取历史")
        }
        Spacer(Modifier.height(8.dp))
        JsonDisplaySection(historyState, emptyLabel = "填写参数后点击「获取历史」")
    }
}

// ── 通用组件 ─────────────────────────────────────────────────────────────────

@Composable
internal fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ErrorText(msg: String) {
    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
