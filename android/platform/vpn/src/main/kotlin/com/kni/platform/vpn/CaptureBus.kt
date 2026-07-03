package com.kni.platform.vpn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class CaptureState { STOPPED, CAPTURING, PAUSED }

/**
 * Process-wide capture status shared between the [KniVpnService] (writer) and the
 * UI/ViewModel (readers). The service runs in the same process as the UI, so a
 * simple in-memory bus is sufficient and avoids IPC.
 */
object CaptureBus {
    private val _state = MutableStateFlow(CaptureState.STOPPED)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _packets = MutableStateFlow(0L)
    val packets: StateFlow<Long> = _packets.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal fun setState(s: CaptureState) { _state.value = s }
    internal fun setPackets(n: Long) { _packets.value = n }
    fun setError(msg: String?) { _error.value = msg }
}

/** Shared filesystem locations. The capture engine (native writer) and the UI's
 *  storage reader must agree on the DB path. */
object KniPaths {
    fun db(context: Context): String = File(context.filesDir, "kni.db").absolutePath

    /** Directory holding the persisted Root CA (see tls-core). */
    fun caDir(context: Context): String = File(context.filesDir, "ca").absolutePath
}
