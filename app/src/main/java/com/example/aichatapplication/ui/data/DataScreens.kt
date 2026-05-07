package com.example.aichatapplication.ui.data

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.aichatapplication.model.EtfBasic
import com.example.aichatapplication.model.EtfDaily
import com.example.aichatapplication.viewmodel.LoadState
import com.example.aichatapplication.viewmodel.StockDataViewModel
import com.google.gson.GsonBuilder

object DataNavRoutes {
    const val HOME = "data_home"
    const val ETF_BASIC = "etf_basic"
    const val ETF_LATEST = "etf_latest"
    const val ETF_DAILY = "etf_daily"
    const val SSE_OVERVIEW = "sse_overview"
    const val HOLDINGS = "holdings"
    const val FUND_FLOW = "fund_flow"
    const val RISE_FALL = "rise_fall"
}

@Composable
fun DataHomeScreen(onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("行情与数据", style = MaterialTheme.typography.headlineSmall)
        Text(
            "以下为后端 OpenAPI 中的常规查询能力，与 AI 对话独立。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HomeNavCard(title = "ETF 基本信息", subtitle = "分页、名称筛选") {
            onOpen(DataNavRoutes.ETF_BASIC)
        }
        HomeNavCard(title = "ETF 最近交易日行情", subtitle = "/etf/daily/latest/") {
            onOpen(DataNavRoutes.ETF_LATEST)
        }
        HomeNavCard(title = "ETF 日线查询", subtitle = "按 ts_code 与日期区间") {
            onOpen(DataNavRoutes.ETF_DAILY)
        }
        HomeNavCard(title = "上证每日概况", subtitle = "/market/sse-daily-overview/") {
            onOpen(DataNavRoutes.SSE_OVERVIEW)
        }
        HomeNavCard(title = "我的自选 / 持仓", subtitle = "需登录，支持添加与删除") {
            onOpen(DataNavRoutes.HOLDINGS)
        }
        HomeNavCard(title = "大盘资金流向", subtitle = "/market/fund-flow/  按日期分页") {
            onOpen(DataNavRoutes.FUND_FLOW)
        }
        HomeNavCard(title = "涨跌比统计", subtitle = "/market/rise-fall-ratio/  近 N 天") {
            onOpen(DataNavRoutes.RISE_FALL)
        }
    }
}

@Composable
private fun HomeNavCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfBasicListScreen(vm: StockDataViewModel, onBack: () -> Unit) {
    val state by vm.etfBasic.collectAsState()
    val filterName by vm.etfBasicFilterName.collectAsState()

    LaunchedEffect(Unit) { vm.loadEtfBasic() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ETF 基本信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = filterName,
                    onValueChange = vm::setEtfBasicFilterName,
                    modifier = Modifier.weight(1f),
                    label = { Text("名称关键字") },
                    singleLine = true
                )
                OutlinedButton(onClick = { vm.loadEtfBasic() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("查询")
                }
            }
            when (val s = state) {
                LoadState.Idle, LoadState.Loading -> {
                    Spacer(Modifier.height(24.dp))
                    BoxCentered { CircularProgressIndicator() }
                }
                is LoadState.Err -> ErrorBlock(s.message)
                is LoadState.Ok -> EtfBasicListContent(
                    modifier = Modifier.weight(1f, fill = true),
                    payload = s.value,
                    onLoadPage = { vm.loadEtfBasic(page = it) }
                )
            }
        }
    }
}

@Composable
private fun EtfBasicListContent(
    modifier: Modifier = Modifier,
    payload: com.example.aichatapplication.model.EtfBasicListPayload,
    onLoadPage: (Int) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "第 ${payload.page} / ${payload.pages} 页，共 ${payload.total} 条",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (payload.page > 1) onLoadPage(payload.page - 1) },
                enabled = payload.page > 1
            ) { Text("上一页") }
            OutlinedButton(
                onClick = { if (payload.page < payload.pages) onLoadPage(payload.page + 1) },
                enabled = payload.page < payload.pages
            ) { Text("下一页") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(payload.items, key = { it.id }) { item ->
                EtfBasicRow(item)
            }
        }
    }
}

@Composable
private fun EtfBasicRow(item: EtfBasic) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${item.tsCode}  ${item.extname}", style = MaterialTheme.typography.titleSmall)
            val sub = listOfNotNull(item.csname, item.indexName, item.exchange, item.listStatus)
                .joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfLatestScreen(vm: StockDataViewModel, onBack: () -> Unit) {
    val state by vm.etfLatest.collectAsState()
    LaunchedEffect(Unit) { vm.loadEtfLatest() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ETF 最新交易日") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.loadEtfLatest() }) { Text("刷新") }
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
                LoadState.Idle, LoadState.Loading -> BoxCentered { CircularProgressIndicator() }
                is LoadState.Err -> ErrorBlock(s.message)
                is LoadState.Ok -> LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.value, key = { "${it.tsCode}_${it.tradeDate}_${it.id}" }) { EtfDailyRow(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfDailyQueryScreen(vm: StockDataViewModel, onBack: () -> Unit) {
    val state by vm.etfDailyQuery.collectAsState()
    var tsCode by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ETF 日线") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = tsCode,
                onValueChange = { tsCode = it },
                label = { Text("ts_code（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = start,
                onValueChange = { start = it },
                label = { Text("start_date（可选 YYYY-MM-DD）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = end,
                onValueChange = { end = it },
                label = { Text("end_date（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { vm.loadEtfDaily(tsCode, start, end) }) {
                Text("查询")
            }
            when (val s = state) {
                LoadState.Idle -> {}
                LoadState.Loading -> BoxCentered { CircularProgressIndicator() }
                is LoadState.Err -> ErrorBlock(s.message)
                is LoadState.Ok -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = true)
                ) {
                    items(s.value, key = { "${it.tsCode}_${it.tradeDate}_${it.id}" }) { EtfDailyRow(it) }
                }
            }
        }
    }
}

@Composable
private fun EtfDailyRow(row: EtfDaily) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${row.tradeDate}  ${row.tsCode}  ${row.csname ?: ""}", style = MaterialTheme.typography.titleSmall)
            val pct = row.pctChg?.let { String.format("涨跌 %.2f%%", it) } ?: ""
            Text(
                "收 ${row.close}  开 ${row.open}  高 ${row.high}  低 ${row.low}  $pct",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SseMarketScreen(vm: StockDataViewModel, onBack: () -> Unit) {
    val state by vm.sseOverview.collectAsState()
    var date by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("上证每日概况") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("日期 YYYYMMDD（可选）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { vm.loadSseOverview(date) }) { Text("获取") }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.weight(1f, fill = true)) {
                when (val s = state) {
                    LoadState.Idle -> Text("点击「获取」加载数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LoadState.Loading -> BoxCentered { CircularProgressIndicator() }
                    is LoadState.Err -> ErrorBlock(s.message)
                    is LoadState.Ok -> {
                        val pretty = remember(s.value) {
                            GsonBuilder().setPrettyPrinting().create().toJson(s.value)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsScreen(vm: StockDataViewModel, onBack: () -> Unit) {
    val state by vm.holdings.collectAsState()
    var code by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("WATCHED") }

    LaunchedEffect(Unit) { vm.loadHoldings() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自选与持仓") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.loadHoldings() }) { Text("刷新") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("股票代码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("HELD/WATCHED") },
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
            }
            OutlinedButton(onClick = { vm.addHolding(code, relation.trim().ifBlank { "WATCHED" }) }) {
                Text("添加")
            }
            when (val s = state) {
                LoadState.Idle, LoadState.Loading -> BoxCentered { CircularProgressIndicator() }
                is LoadState.Err -> ErrorBlock(s.message)
                is LoadState.Ok -> {
                    val rows = remember(s.value) { vm.extractHoldingRows(s.value) }
                    if (rows.isEmpty()) {
                        Text("暂无记录，或返回结构与客户端解析不一致。原始 JSON：", style = MaterialTheme.typography.labelMedium)
                        Text(
                            GsonBuilder().setPrettyPrinting().create().toJson(s.value),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = true),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rows, key = { it.id }) { r ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(r.stockCode, style = MaterialTheme.typography.titleSmall)
                                            r.relationType?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        }
                                        TextButton(onClick = { vm.deleteHolding(r.id) }) { Text("删除") }
                                    }
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
private fun BoxCentered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ErrorBlock(msg: String) {
    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

// ── 大盘资金流向 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundFlowScreen(vm: StockDataViewModel, onBack: () -> Unit) {
    val state by vm.fundFlow.collectAsState()
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大盘资金流向") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.loadFundFlow(startDate, endDate) }) { Text("查询") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("start YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("end YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(modifier = Modifier.weight(1f, fill = true)) {
                when (val s = state) {
                    LoadState.Idle -> {
                        LaunchedEffect(Unit) { vm.loadFundFlow() }
                        BoxCentered { CircularProgressIndicator() }
                    }
                    LoadState.Loading -> BoxCentered { CircularProgressIndicator() }
                    is LoadState.Err -> ErrorBlock(s.message)
                    is LoadState.Ok -> JsonTableView(json = s.value, vm = vm)
                }
            }
        }
    }
}

// ── 涨跌比 ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiseFallScreen(vm: StockDataViewModel, onBack: () -> Unit) {
    val state by vm.riseFallRatio.collectAsState()
    var indexCode by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("30") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("涨跌比统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.loadRiseFallRatio(indexCode, limit.toIntOrNull() ?: 30) }) { Text("查询") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = indexCode,
                    onValueChange = { indexCode = it },
                    label = { Text("指数代码（可选）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("最近 N 天") },
                    singleLine = true,
                    modifier = Modifier.weight(0.5f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Box(modifier = Modifier.weight(1f, fill = true)) {
                when (val s = state) {
                    LoadState.Idle -> {
                        LaunchedEffect(Unit) { vm.loadRiseFallRatio() }
                        BoxCentered { CircularProgressIndicator() }
                    }
                    LoadState.Loading -> BoxCentered { CircularProgressIndicator() }
                    is LoadState.Err -> ErrorBlock(s.message)
                    is LoadState.Ok -> JsonTableView(json = s.value, vm = vm)
                }
            }
        }
    }
}

/** 将 JsonElement 解析为行/列表格展示 */
@Composable
private fun JsonTableView(json: com.google.gson.JsonElement, vm: StockDataViewModel) {
    val rows = remember(json) { vm.jsonToRows(json) }
    if (rows.isEmpty()) {
        val pretty = remember(json) { com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json) }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(pretty, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val headers = remember(rows) { rows.first().keys.toList() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(rows) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    headers.take(6).forEach { key ->
                        val value = row[key] ?: ""
                        if (value.isNotBlank()) {
                            Row {
                                Text(
                                    "$key: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.45f)
                                )
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(0.55f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
