package com.kni.platform.vpn

class NativeVpnEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }
        fun create(tunFd: Int): NativeVpnEngine = NativeVpnEngine(vpn_engine_init(tunFd))

        @JvmStatic
        private external fun vpn_engine_init(tunFd: Int): Long
        @JvmStatic
        private external fun vpn_engine_free(enginePtr: Long)
        @JvmStatic
        private external fun vpn_engine_read_packets(enginePtr: Long): Int
    }

    fun readPackets(): Int {
        return vpn_engine_read_packets(nativePtr)
    }

    fun destroy() {
        vpn_engine_free(nativePtr)
    }
}
