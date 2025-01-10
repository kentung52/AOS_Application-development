package com.example.budgetmanager.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiService {
    private const val BASE_URL = "https://tw.rter.info/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val exchangeRateApi: ExchangeRateApi = retrofit.create(ExchangeRateApi::class.java)
}
