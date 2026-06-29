package com.kni.platform.parser

class NativeHttpEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }
        fun create(): NativeHttpEngine = NativeHttpEngine(http_engine_new())
    }

    fun parseStream(data: ByteArray): ByteArray {
        return http_engine_parse_stream(nativePtr, data)
    }

    fun destroy() {
        http_engine_free(nativePtr)
    }

    private external fun http_engine_new(): Long
    private external fun http_engine_free(enginePtr: Long)
    private external fun http_engine_parse_stream(enginePtr: Long, streamData: ByteArray): ByteArray
}
