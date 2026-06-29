package com.kni.app.data

data class NetworkTransaction(
    val id: String,
    val method: String,
    val url: String,
    val status: Int,
    val duration: String,
    val size: String,
    val timestamp: Long
)
