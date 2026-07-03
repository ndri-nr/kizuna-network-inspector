package com.kni.app.data

import android.content.Context
import com.kni.platform.vpn.KniPaths
import com.kni.platform.storage.NativeStorageEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Reads captured exchanges from the shared SQLite store (written by the native
 * capture engine) and exposes them as observable state. [refresh] is polled by
 * the ViewModel; new rows are deduplicated by id and prepended (newest first).
 */
class TransactionRepository(context: Context) {
    private val storage = NativeStorageEngine.create(KniPaths.db(context))

    private val _transactions = MutableStateFlow<List<NetworkTransaction>>(emptyList())
    val transactions: StateFlow<List<NetworkTransaction>> = _transactions.asStateFlow()

    private val seen = HashSet<String>()

    @Volatile
    private var excludedHosts: List<String> = emptyList()

    /** Hosts to exclude from the feed (substring match), from Settings > Domain Filters. */
    fun setExcludedHosts(hosts: List<String>) {
        excludedHosts = hosts
    }

    private fun isExcluded(host: String): Boolean =
        excludedHosts.any { host.contains(it, ignoreCase = true) }

    /** Pull the newest rows and merge any not seen before. */
    fun refresh() {
        val engine = storage ?: return
        val json = engine.readSince(0)
        val arr = org.json.JSONArray(json)
        val fresh = ArrayList<NetworkTransaction>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.getString("id")
            if (!seen.add(id)) continue
            val tx = parse(obj)
            if (isExcluded(tx.host)) continue
            fresh.add(tx)
        }
        if (fresh.isNotEmpty()) {
            _transactions.value = fresh + _transactions.value
        }
    }

    fun getById(id: String): NetworkTransaction? {
        val engine = storage ?: return null
        val json = engine.readById(id)
        if (json == "null" || json.isBlank()) return null
        return parse(JSONObject(json))
    }

    fun count(): Long = storage?.count() ?: 0

    fun clear() {
        _transactions.value = emptyList()
        seen.clear()
    }

    private fun parse(o: JSONObject): NetworkTransaction = NetworkTransaction(
        id = o.optString("id"),
        scheme = o.optString("scheme", "http"),
        host = o.optString("host"),
        method = o.optString("method"),
        url = o.optString("url"),
        status = if (o.isNull("status_code")) 0 else o.optInt("status_code", 0),
        durationMs = if (o.isNull("duration_ms")) 0L else o.optLong("duration_ms", 0L),
        reqSize = o.optLong("req_size", 0L),
        respSize = o.optLong("resp_size", 0L),
        timestamp = o.optLong("timestamp", 0L),
        requestHeaders = o.optString("request_headers", "{}"),
        responseHeaders = o.optString("response_headers", "{}"),
        requestBody = o.optString("request_body", ""),
        responseBody = o.optString("response_body", "")
    )
}
