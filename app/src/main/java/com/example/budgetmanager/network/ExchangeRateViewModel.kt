package com.example.budgetmanager.network

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch


class ExchangeRateViewModel : ViewModel() {
    var currencyRates = mutableStateOf<Map<String, CurrencyRate>>(emptyMap())
        private set

    var errorMessage = mutableStateOf("")
        private set

    fun fetchExchangeRates() {
        viewModelScope.launch {
            try {
                val response = ApiService.exchangeRateApi.getAllRates()
                // 解析資料：去掉 "USD" 前綴，使用目標貨幣代碼作為鍵
                currencyRates.value = response.mapKeys {
                    it.key.substring(3)
                }
            } catch (e: Exception) {
                errorMessage.value = "無法載入匯率數據：${e.message}"
            }
        }
    }
}
