package com.kni.ui.compose.screens

/** Full detail for one exchange, resolved by id when the Detail screen opens. */
data class DetailData(
    val method: String,
    val url: String,
    val status: Int,
    val scheme: String,
    val host: String,
    val durationMs: Long,
    val reqSize: Long,
    val respSize: Long,
    val requestHeaders: String,
    val responseHeaders: String,
    val requestBody: String,
    val responseBody: String,
    val tlsVersion: String,
    val tlsCipher: String,
    val tlsCert: String
)

/** One installed app, for the capture app-picker. */
data class AppInfo(val packageName: String, val label: String)

/** Settings actions/state, provided by the app layer. */
class SettingsHooks(
    val onInstallCert: () -> Unit,
    val onExportCert: () -> Unit,
    val onSaveCert: () -> Unit,
    val storageLimitMb: Int,
    val onSetStorageLimitMb: (Int) -> Unit,
    val domainFilters: List<String>,
    val onSetDomainFilters: (List<String>) -> Unit,
    val isBatteryOptimized: Boolean,
    val onRequestIgnoreBatteryOptimizations: () -> Unit,
    /** HTTPS MITM decryption toggle. */
    val decryptHttps: Boolean,
    val onSetDecryptHttps: (Boolean) -> Unit,
    /** Apps available to capture, and the currently selected subset (empty = all). */
    val installedApps: List<AppInfo>,
    val selectedApps: Set<String>,
    val onSetSelectedApps: (Set<String>) -> Unit
)

/** Bundle of cross-screen data providers/actions supplied by the app layer. */
class ScreenHooks(
    val loadDetail: (String) -> DetailData?,
    val loadDiagnostics: () -> List<Pair<String, String>>,
    val settings: SettingsHooks
)
