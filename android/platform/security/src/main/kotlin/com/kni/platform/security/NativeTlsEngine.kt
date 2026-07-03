package com.kni.platform.security

/**
 * JVM handle to the native Root CA. The CA keypair is generated and persisted in
 * Rust (`tls-core`); only the public certificate PEM is exposed here so the user
 * can install it as a trusted root. The private key never crosses this boundary.
 */
class NativeTlsEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }

        /** @param caDir directory where the CA is persisted (created if absent). */
        fun create(caDir: String): NativeTlsEngine? {
            val ptr = tls_engine_new(caDir)
            return if (ptr != 0L) NativeTlsEngine(ptr) else null
        }

        @JvmStatic
        private external fun tls_engine_new(caDir: String): Long
        @JvmStatic
        private external fun tls_engine_get_ca_pem(enginePtr: Long): ByteArray
        @JvmStatic
        private external fun tls_engine_free(enginePtr: Long)
    }

    /** PEM bytes of the Root CA certificate. */
    fun caCertPem(): ByteArray = tls_engine_get_ca_pem(nativePtr)

    fun destroy() = tls_engine_free(nativePtr)
}
