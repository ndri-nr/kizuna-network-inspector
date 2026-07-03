package com.kni.platform.storage

/**
 * Read/write access to the shared SQLite capture store. Reads return JSON strings
 * (decoded with `org.json` on the Kotlin side); the native capture engine is the
 * primary writer, so [writeExchange] is rarely used from the JVM.
 */
class NativeStorageEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }

        fun create(dbPath: String): NativeStorageEngine? {
            val ptr = storage_engine_init(dbPath)
            return if (ptr != 0L) NativeStorageEngine(ptr) else null
        }

        @JvmStatic
        private external fun storage_engine_init(dbPath: String): Long
        @JvmStatic
        private external fun storage_engine_free(enginePtr: Long)
        @JvmStatic
        private external fun storage_engine_write_exchange(enginePtr: Long, cbor: ByteArray): Int
        @JvmStatic
        private external fun storage_engine_read_since(enginePtr: Long, since: Long): String
        @JvmStatic
        private external fun storage_engine_read_by_id(enginePtr: Long, id: String): String
        @JvmStatic
        private external fun storage_engine_count(enginePtr: Long): Long
        @JvmStatic
        private external fun storage_engine_delete_by_ids(enginePtr: Long, idsJson: String): Int
        @JvmStatic
        private external fun storage_engine_delete_all(enginePtr: Long): Int
    }

    /** JSON array of exchanges with timestamp > [since], newest first. */
    fun readSince(since: Long): String = storage_engine_read_since(nativePtr, since)

    /** JSON object for [id], or the string "null". */
    fun readById(id: String): String = storage_engine_read_by_id(nativePtr, id)

    fun count(): Long = storage_engine_count(nativePtr)

    fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val arr = org.json.JSONArray(ids)
        storage_engine_delete_by_ids(nativePtr, arr.toString())
    }

    fun deleteAll() {
        storage_engine_delete_all(nativePtr)
    }

    fun destroy() = storage_engine_free(nativePtr)
}
