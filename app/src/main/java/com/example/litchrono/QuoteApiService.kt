package com.example.litchrono

import retrofit2.Call
import retrofit2.http.GET

interface QuoteApiService {
    @GET("time_of_day_quotes_with_bold.json")
    fun getQuotes(): Call<Map<String, List<Quote>>>
}
