package com.example.aichatapplication.model

import com.google.gson.annotations.SerializedName

data class EtfBasicListPayload(
    val items: List<EtfBasic> = emptyList(),
    val page: Int = 1,
    @SerializedName("page_size")
    val pageSize: Int = 20,
    val total: Int = 0,
    val pages: Int = 0
)

data class EtfBasic(
    val id: Long,
    @SerializedName("ts_code")
    val tsCode: String,
    val csname: String? = null,
    val extname: String,
    val cname: String? = null,
    @SerializedName("index_code")
    val indexCode: String? = null,
    @SerializedName("index_name")
    val indexName: String? = null,
    val exchange: String? = null,
    @SerializedName("list_status")
    val listStatus: String? = null,
    @SerializedName("etf_type")
    val etfType: String? = null,
    @SerializedName("mgr_name")
    val mgrName: String? = null,
    @SerializedName("custod_name")
    val custodName: String? = null,
    @SerializedName("setup_date")
    val setupDate: String? = null,
    @SerializedName("list_date")
    val listDate: String? = null,
    @SerializedName("delist_date")
    val delistDate: String? = null,
    @SerializedName("mgt_fee")
    val mgtFee: Double? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

data class EtfDaily(
    val id: Long,
    @SerializedName("trade_date")
    val tradeDate: String,
    @SerializedName("ts_code")
    val tsCode: String,
    val csname: String? = null,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    @SerializedName("pre_close")
    val preClose: Double? = null,
    val change: Double? = null,
    @SerializedName("pct_chg")
    val pctChg: Double? = null,
    val vol: Long,
    val amount: Double,
    @SerializedName("created_at")
    val createdAt: String? = null
)
