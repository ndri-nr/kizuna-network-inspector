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
    val responseBody: String
)

/** Settings actions/state, provided by the app layer. */
class SettingsHooks(
    val onInstallCert: () -> Unit,
    val onExportCert: () -> Unit,
    val storageLimitMb: Int,
    val onSetStorageLimitMb: (Int) -> Unit,
    val domainFilters: List<String>,
    val onSetDomainFilters: (List<String>) -> Unit
)

/** Bundle of cross-screen data providers/actions supplied by the app layer. */
class ScreenHooks(
    val loadDetail: (String) -> DetailData?,
    val loadDiagnostics: () -> List<Pair<String, String>>,
    val settings: SettingsHooks
)
