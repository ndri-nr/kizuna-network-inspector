package com.kni.platform.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Establishes the VPN tun and hands its fd to the native capture engine, which
 * relays traffic to `protect()`ed upstream sockets while teeing it into the
 * inspector. Runs as a foreground service (required for a VPN on modern Android).
 */
class KniVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var engine: NativeVpnEngine? = null
    private var captureThread: Thread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
            ACTION_PAUSE -> {
                engine?.setPaused(true)
                CaptureBus.setState(CaptureState.PAUSED)
                updateNotification("Paused")
            }
            ACTION_RESUME -> {
                engine?.setPaused(false)
                CaptureBus.setState(CaptureState.CAPTURING)
                updateNotification("Capturing traffic")
            }
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (running) return
        CaptureBus.setError(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                buildNotification("Starting…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification("Starting…"))
        }

        val builder = Builder()
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setSession("Kizuna Network Inspector")

        val iface = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish() threw", e)
            null
        }
        if (iface == null) {
            CaptureBus.setError("Failed to establish VPN (permission not granted?)")
            stopSelfCleanup()
            return
        }
        vpnInterface = iface

        val eng = NativeVpnEngine.create(iface.fd, KniPaths.db(this), this)
        if (eng == null) {
            CaptureBus.setError("Native capture engine failed to initialize")
            stopSelfCleanup()
            return
        }
        engine = eng
        running = true
        CaptureBus.setState(CaptureState.CAPTURING)
        updateNotification("Capturing traffic")

        captureThread = Thread({
            val code = eng.run() // blocks until stop()
            if (code < 0) Log.e(TAG, "capture loop exited with $code")
        }, "KniCaptureLoop").apply { start() }

        // Publish packet stats for Diagnostics.
        scope.launch {
            while (isActive && running) {
                CaptureBus.setPackets(eng.stats())
                delay(1000)
            }
        }
    }

    private fun stopCapture() {
        if (!running) {
            stopSelfCleanup()
            return
        }
        running = false
        engine?.stop()
        try {
            captureThread?.join(2000)
        } catch (_: InterruptedException) {
        }
        captureThread = null
        engine?.destroy()
        engine = null
        closeInterface()
        CaptureBus.setState(CaptureState.STOPPED)
        stopForegroundCompat()
        stopSelf()
    }

    /** Cleanup path when startup fails before the loop is running. */
    private fun stopSelfCleanup() {
        closeInterface()
        CaptureBus.setState(CaptureState.STOPPED)
        stopForegroundCompat()
        stopSelf()
    }

    private fun closeInterface() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
    }

    private fun buildNotification(text: String): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capture Status",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kizuna Network Inspector")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KniVpnService"
        private const val CHANNEL_ID = "kni_capture"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.kni.vpn.START"
        const val ACTION_STOP = "com.kni.vpn.STOP"
        const val ACTION_PAUSE = "com.kni.vpn.PAUSE"
        const val ACTION_RESUME = "com.kni.vpn.RESUME"
    }
}
