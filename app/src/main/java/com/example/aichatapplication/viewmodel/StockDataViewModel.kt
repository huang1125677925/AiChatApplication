package com.example.aichatapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichatapplication.model.EtfBasicListPayload
import com.example.aichatapplication.model.EtfDaily
import com.example.aichatapplication.network.StockDataApiClient
import com.example.aichatapplication.utils.SessionManager
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoadState<out T> {
    data object Idle : LoadState<Nothing>()
    data object Loading : LoadState<Nothing>()
    data class Ok<T>(val value: T) : LoadState<T>()
    data class Err(val message: String) : LoadState<Nothing>()
}

class StockDataViewModel(application: Application) : AndroidViewModel(application) {
    private val client = StockDataApiClient()
    private val sessionManager = SessionManager(application)

    private fun tokenOrNull(): String? = sessionManager.getToken()

    private val _etfBasic = MutableStateFlow<LoadState<EtfBasicListPayload>>(LoadState.Idle)
    val etfBasic: StateFlow<LoadState<EtfBasicListPayload>> = _etfBasic.asStateFlow()

    private val _etfBasicFilterName = MutableStateFlow("")
    val etfBasicFilterName: StateFlow<String> = _etfBasicFilterName.asStateFlow()

    private val _etfLatest = MutableStateFlow<LoadState<List<EtfDaily>>>(LoadState.Idle)
    val etfLatest: StateFlow<LoadState<List<EtfDaily>>> = _etfLatest.asStateFlow()

    private val _etfDailyQuery = MutableStateFlow<LoadState<List<EtfDaily>>>(LoadState.Idle)
    val etfDailyQuery: StateFlow<LoadState<List<EtfDaily>>> = _etfDailyQuery.asStateFlow()

    private val _sseOverview = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val sseOverview: StateFlow<LoadState<JsonElement>> = _sseOverview.asStateFlow()

    private val _holdings = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val holdings: StateFlow<LoadState<JsonElement>> = _holdings.asStateFlow()

    private val _dataMessage = MutableStateFlow<String?>(null)
    val dataMessage: StateFlow<String?> = _dataMessage.asStateFlow()

    fun setEtfBasicFilterName(value: String) {
        _etfBasicFilterName.value = value
    }

    fun consumeDataMessage() {
        _dataMessage.value = null
    }

    fun loadEtfBasic(page: Int = 1, pageSize: Int = 20) {
        viewModelScope.launch(Dispatchers.IO) {
            _etfBasic.value = LoadState.Loading
            _etfBasic.value = runCatching {
                client.getEtfBasic(
                    token = tokenOrNull(),
                    page = page,
                    pageSize = pageSize,
                    name = _etfBasicFilterName.value.trim().ifBlank { null }
                )
            }.fold(
                onSuccess = { LoadState.Ok(it) },
                onFailure = { LoadState.Err(it.message ?: it.toString()) }
            )
        }
    }

    fun loadEtfLatest() {
        viewModelScope.launch(Dispatchers.IO) {
            _etfLatest.value = LoadState.Loading
            _etfLatest.value = runCatching {
                client.getEtfDailyLatest(token = tokenOrNull())
            }.fold(
                onSuccess = { LoadState.Ok(it) },
                onFailure = { LoadState.Err(it.message ?: it.toString()) }
            )
        }
    }

    fun loadEtfDaily(tsCode: String, startDate: String?, endDate: String?) {
        if (tsCode.isBlank()) {
            _etfDailyQuery.value = LoadState.Err("请填写 ETF 代码（ts_code）")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _etfDailyQuery.value = LoadState.Loading
            _etfDailyQuery.value = runCatching {
                client.getEtfDaily(
                    token = tokenOrNull(),
                    tsCode = tsCode,
                    startDate = startDate?.trim()?.ifBlank { null },
                    endDate = endDate?.trim()?.ifBlank { null }
                )
            }.fold(
                onSuccess = { LoadState.Ok(it) },
                onFailure = { LoadState.Err(it.message ?: it.toString()) }
            )
        }
    }

    fun loadSseOverview(date: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _sseOverview.value = LoadState.Loading
            _sseOverview.value = runCatching {
                client.getSseDailyOverview(token = tokenOrNull(), date = date?.trim()?.ifBlank { null })
            }.fold(
                onSuccess = { LoadState.Ok(it) },
                onFailure = { LoadState.Err(it.message ?: it.toString()) }
            )
        }
    }

    fun loadHoldings() {
        val token = tokenOrNull()
        if (token.isNullOrBlank()) {
            _holdings.value = LoadState.Err("请先登录后再查看自选/持仓")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _holdings.value = LoadState.Loading
            _holdings.value = runCatching {
                client.listHoldings(token)
            }.fold(
                onSuccess = { LoadState.Ok(it) },
                onFailure = { LoadState.Err(it.message ?: it.toString()) }
            )
        }
    }

    fun addHolding(stockCode: String, relationType: String = "WATCHED") {
        val token = tokenOrNull()
        if (token.isNullOrBlank()) {
            _dataMessage.value = "请先登录"
            return
        }
        if (stockCode.isBlank()) {
            _dataMessage.value = "股票代码不能为空"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                client.addHolding(token = token, stockCode = stockCode, relationType = relationType)
            }
            result.onSuccess {
                _dataMessage.value = "添加成功"
                loadHoldings()
            }.onFailure {
                _dataMessage.value = it.message ?: it.toString()
            }
        }
    }

    fun deleteHolding(holdingId: Int) {
        val token = tokenOrNull()
        if (token.isNullOrBlank()) {
            _dataMessage.value = "请先登录"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client.deleteHolding(token, holdingId) }
                .onSuccess {
                    _dataMessage.value = "已删除"
                    loadHoldings()
                }
                .onFailure {
                    _dataMessage.value = it.message ?: it.toString()
                }
        }
    }

    fun extractHoldingRows(json: JsonElement): List<HoldingRow> {
        val root = json.asJsonObject
        val list = when {
            root.has("list") && root.get("list").isJsonArray -> root.getAsJsonArray("list")
            root.has("items") && root.get("items").isJsonArray -> root.getAsJsonArray("items")
            json.isJsonArray -> json.asJsonArray
            else -> null
        } ?: return emptyList()
        val out = ArrayList<HoldingRow>()
        for (el in list) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            val id = when {
                o.has("id") && o.get("id").isJsonPrimitive && o.get("id").asJsonPrimitive.isNumber ->
                    o.get("id").asInt
                else -> null
            }
            val code = when {
                o.has("stock_code") -> o.get("stock_code")?.asString
                o.has("ts_code") -> o.get("ts_code")?.asString
                else -> null
            }
            val relation = if (o.has("relation_type")) o.get("relation_type")?.asString else null
            if (id != null && !code.isNullOrBlank()) {
                out.add(HoldingRow(id = id, stockCode = code, relationType = relation))
            }
        }
        return out
    }

    data class HoldingRow(
        val id: Int,
        val stockCode: String,
        val relationType: String?
    )

    // ── 个股 ────────────────────────────────────────────────────────────────────

    private val _stocks = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val stocks: StateFlow<LoadState<JsonElement>> = _stocks.asStateFlow()

    private val _stockKeyword = MutableStateFlow("")
    val stockKeyword: StateFlow<String> = _stockKeyword.asStateFlow()

    private val _stockDetail = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val stockDetail: StateFlow<LoadState<JsonElement>> = _stockDetail.asStateFlow()

    private val _stockRealtime = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val stockRealtime: StateFlow<LoadState<JsonElement>> = _stockRealtime.asStateFlow()

    private val _stockHistory = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val stockHistory: StateFlow<LoadState<JsonElement>> = _stockHistory.asStateFlow()

    fun setStockKeyword(v: String) { _stockKeyword.value = v }

    fun loadStocks(page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _stocks.value = LoadState.Loading
            _stocks.value = runCatching {
                client.getStocks(
                    token = tokenOrNull(),
                    keyword = _stockKeyword.value.trim().ifBlank { null },
                    page = page
                )
            }.fold(
                onSuccess = { LoadState.Ok(it) },
                onFailure = { LoadState.Err(it.message ?: it.toString()) }
            )
        }
    }

    fun loadStockDetail(code: String) {
        _stockDetail.value = LoadState.Idle
        _stockRealtime.value = LoadState.Idle
        val c = code.trim()
        if (c.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _stockDetail.value = LoadState.Loading
            _stockDetail.value = runCatching { client.getStockInfo(tokenOrNull(), c) }
                .fold(onSuccess = { LoadState.Ok(it) }, onFailure = { LoadState.Err(it.message ?: it.toString()) })
        }
        viewModelScope.launch(Dispatchers.IO) {
            _stockRealtime.value = LoadState.Loading
            _stockRealtime.value = runCatching { client.getStockRealtime(tokenOrNull(), c) }
                .fold(onSuccess = { LoadState.Ok(it) }, onFailure = { LoadState.Err(it.message ?: it.toString()) })
        }
    }

    fun loadStockHistory(code: String, startDate: String?, endDate: String?, adjust: String?) {
        val c = code.trim()
        if (c.isBlank()) { _stockHistory.value = LoadState.Err("请输入股票代码"); return }
        viewModelScope.launch(Dispatchers.IO) {
            _stockHistory.value = LoadState.Loading
            _stockHistory.value = runCatching {
                client.getStockHistory(tokenOrNull(), c,
                    startDate?.trim()?.ifBlank { null },
                    endDate?.trim()?.ifBlank { null },
                    adjust?.trim()?.ifBlank { null })
            }.fold(onSuccess = { LoadState.Ok(it) }, onFailure = { LoadState.Err(it.message ?: it.toString()) })
        }
    }

    // ── 大盘 ────────────────────────────────────────────────────────────────────

    private val _fundFlow = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val fundFlow: StateFlow<LoadState<JsonElement>> = _fundFlow.asStateFlow()

    private val _riseFallRatio = MutableStateFlow<LoadState<JsonElement>>(LoadState.Idle)
    val riseFallRatio: StateFlow<LoadState<JsonElement>> = _riseFallRatio.asStateFlow()

    fun loadFundFlow(startDate: String? = null, endDate: String? = null, page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _fundFlow.value = LoadState.Loading
            _fundFlow.value = runCatching {
                client.getFundFlow(tokenOrNull(),
                    startDate?.trim()?.ifBlank { null },
                    endDate?.trim()?.ifBlank { null },
                    page)
            }.fold(onSuccess = { LoadState.Ok(it) }, onFailure = { LoadState.Err(it.message ?: it.toString()) })
        }
    }

    fun loadRiseFallRatio(indexCode: String? = null, limit: Int = 30) {
        viewModelScope.launch(Dispatchers.IO) {
            _riseFallRatio.value = LoadState.Loading
            _riseFallRatio.value = runCatching {
                client.getRiseFallRatio(tokenOrNull(), indexCode?.trim()?.ifBlank { null }, limit)
            }.fold(onSuccess = { LoadState.Ok(it) }, onFailure = { LoadState.Err(it.message ?: it.toString()) })
        }
    }

    // ── JSON 解析工具 ────────────────────────────────────────────────────────────

    fun jsonToRows(json: JsonElement): List<Map<String, String>> {
        val arr = when {
            json.isJsonArray -> json.asJsonArray
            json.isJsonObject -> {
                val o = json.asJsonObject
                listOf("list", "items", "results", "data").firstNotNullOfOrNull { k ->
                    if (o.has(k) && o.get(k).isJsonArray) o.getAsJsonArray(k) else null
                } ?: return listOf()
            }
            else -> return listOf()
        }
        return arr.mapNotNull { el ->
            if (!el.isJsonObject) null
            else el.asJsonObject.entrySet().associate { (k, v) ->
                k to (runCatching { v.asString }.getOrElse { v.toString() })
            }
        }
    }
}
