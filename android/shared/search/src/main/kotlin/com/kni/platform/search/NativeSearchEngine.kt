package com.kni.platform.search

class NativeSearchEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }
        fun create(dbPath: String): NativeSearchEngine = NativeSearchEngine(search_engine_new(dbPath))

        @JvmStatic
        private external fun search_engine_new(dbPath: String): Long
        @JvmStatic
        private external fun search_engine_free(enginePtr: Long)
        @JvmStatic
        private external fun search_engine_query(enginePtr: Long, queryStr: String): ByteArray
    }

    fun query(searchQuery: String): ByteArray {
        return search_engine_query(nativePtr, searchQuery)
    }

    fun destroy() {
        search_engine_free(nativePtr)
    }
}
