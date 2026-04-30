package com.example.litchrono

import com.google.gson.annotations.SerializedName

data class Quote(
    @SerializedName("quote")
    val text: String,
    val title: String,
    val author: String,
    val asin: String
)
