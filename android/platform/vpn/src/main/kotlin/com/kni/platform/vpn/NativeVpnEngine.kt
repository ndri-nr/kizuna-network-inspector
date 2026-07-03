package com.kni.platform.vpn

import android.net.VpnService

/**
 * JVM handle to the native capture engine. [run] blocks the calling thread and
 * drives the smoltcp relay loop until [stop] is called from another thread.
 */
class NativeVpnEngine private constructor(private val nativePtr: Long) {
    companion object {
        init {
            System.loadLibrary("kni_rust_core")
        }

        /**
         * @param service the [VpnService] instance; the native loop calls
         *   `service.protect(fd)` on each upstream socket to keep relayed traffic
         *   out of the tunnel.
         * @return null if native initialization failed.
         */
        fun create(tunFd: Int, dbPath: String, service: VpnService): NativeVpnEngine? {
            val ptr = vpn_engine_init(tunFd, dbPath, service)
            return if (ptr != 0L) NativeVpnEngine(ptr) else null
        }

        @JvmStatic
        private external fun vpn_engine_init(tunFd: Int, dbPath: String, service: VpnService): Long
        @JvmStatic
        private external fun vpn_engine_run(enginePtr: Long): Int
        @JvmStatic
        private external fun vpn_engine_stop(enginePtr: Long)
        @JvmStatic
        private external fun vpn_engine_set_paused(enginePtr: Long, paused: Boolean)
        @JvmStatic
        private external fun vpn_engine_stats(enginePtr: Long): Long
        @JvmStatic
        private external fun vpn_engine_free(enginePtr: Long)
    }

    /** Blocks until [stop] is called. Returns 0 on clean exit, negative on error. */
    fun run(): Int = vpn_engine_run(nativePtr)

    fun stop() = vpn_engine_stop(nativePtr)

    fun setPaused(paused: Boolean) = vpn_engine_set_paused(nativePtr, paused)

    /** Packets processed since start. */
    fun stats(): Long = vpn_engine_stats(nativePtr)

    fun destroy() = vpn_engine_free(nativePtr)
}
