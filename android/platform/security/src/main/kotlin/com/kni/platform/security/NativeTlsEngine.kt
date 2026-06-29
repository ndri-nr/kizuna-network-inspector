package com.kni.platform.security

class NativeTlsEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }
        fun create(rootCaPem: ByteArray): NativeTlsEngine {
            return NativeTlsEngine(tls_engine_new(rootCaPem))
        }
    }

    fun interceptHandshake(connectionId: Long, sni: String): Int {
        return tls_engine_intercept_handshake(nativePtr, connectionId, sni)
    }

    fun destroy() {
        tls_engine_free(nativePtr)
    }

    private external fun tls_engine_new(caPem: ByteArray): Long
    private external fun tls_engine_free(enginePtr: Long)
    private external fun tls_engine_intercept_handshake(enginePtr: Long, connId: Long, sni: String): Int
}
