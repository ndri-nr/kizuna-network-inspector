package com.kni.app.data

/** A captured HTTP(S) exchange, mirroring the Rust `HttpExchange` record. */
data class NetworkTransaction(
    val id: String,
    val scheme: String,
    val host: String,
    val method: String,
    val url: String,
    val status: Int,
    val durationMs: Long,
    val reqSize: Long,
    val respSize: Long,
    val timestamp: Long,
    val requestHeaders: String,
    val responseHeaders: String,
    val requestBody: String,
    val responseBody: String
)
