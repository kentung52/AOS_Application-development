package com.example.budgetmanager.network

import retrofit2.http.GET

interface ExchangeRateApi {
    @GET("capi.php")
    suspend fun getAllRates(): Map<String, CurrencyRate>
}

// 定義 CurrencyRate 資料類型
data class CurrencyRate(
    val Exrate: Double,  // 匯率
    val Date: String     // 日期
)
