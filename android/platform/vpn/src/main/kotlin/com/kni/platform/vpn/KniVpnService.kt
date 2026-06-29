package com.kni.platform.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class KniVpnService : VpnService(), Runnable {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var nativeEngine: NativeVpnEngine? = null
    private var isRunning = false

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_VPN") {
            startVpn()
        } else if (intent?.action == "STOP_VPN") {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true

        val builder = Builder()
        builder.setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setSession("KizunaVPN")

        vpnInterface = builder.establish()
        val fd = vpnInterface?.fd ?: return

        nativeEngine = NativeVpnEngine.create(fd)
        vpnThread = Thread(this, "KniVpnThread").apply { start() }
    }

    override fun run() {
        Log.i("KniVpnService", "VPN packet capture loop started.")
        while (isRunning) {
            val result = nativeEngine?.readPackets() ?: -1
            if (result < 0) {
                Log.e("KniVpnService", "Error reading packets from native engine: $result")
                break
            }
        }
        stopVpn()
    }

    private fun stopVpn() {
        if (!isRunning) return
        isRunning = false

        nativeEngine?.destroy()
        nativeEngine = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("KniVpnService", "Error closing VPN interface", e)
        }
        vpnInterface = null
        Log.i("KniVpnService", "VPN packet capture loop stopped.")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
