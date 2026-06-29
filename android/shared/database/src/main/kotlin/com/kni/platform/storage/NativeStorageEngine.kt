package com.kni.platform.storage

class NativeStorageEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }
        fun create(dbPath: String): NativeStorageEngine = NativeStorageEngine(storage_engine_init(dbPath))
    }

    fun writeExchange(exchangeCbor: ByteArray): Int {
        return storage_engine_write_exchange(nativePtr, exchangeCbor)
    }

    fun destroy() {
        storage_engine_free(nativePtr)
    }

    private external fun storage_engine_init(dbPath: String): Long
    private external fun storage_engine_free(enginePtr: Long)
    private external fun storage_engine_write_exchange(enginePtr: Long, cbor: ByteArray): Int
}
