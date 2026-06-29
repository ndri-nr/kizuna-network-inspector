package com.kni.platform.search

class NativeSearchEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }
        fun create(dbPath: String): NativeSearchEngine = NativeSearchEngine(search_engine_new(dbPath))
    }

    fun query(searchQuery: String): ByteArray {
        return search_engine_query(nativePtr, searchQuery)
    }

    fun destroy() {
        search_engine_free(nativePtr)
    }

    private external fun search_engine_new(dbPath: String): Long
    private external fun search_engine_free(enginePtr: Long)
    private external fun search_engine_query(enginePtr: Long, queryStr: String): ByteArray
}
