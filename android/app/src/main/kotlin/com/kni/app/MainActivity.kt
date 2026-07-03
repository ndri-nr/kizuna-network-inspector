package com.kni.app

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.security.KeyChain
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kni.app.data.NetworkTransaction
import com.kni.app.data.SettingsRepository
import com.kni.app.data.TransactionRepository
import com.kni.app.viewmodel.CaptureViewModel
import com.kni.platform.security.cert.CertificateManager
import com.kni.platform.vpn.CaptureBus
import com.kni.platform.vpn.CaptureState
import com.kni.platform.vpn.KniPaths
import com.kni.platform.vpn.KniVpnService
import com.kni.ui.compose.MainScreen
import com.kni.ui.compose.screens.DetailData
import com.kni.ui.compose.screens.LogItemData
import com.kni.ui.compose.screens.ScreenHooks
import com.kni.ui.compose.screens.SettingsHooks
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val repository by lazy { TransactionRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val certManager by lazy { CertificateManager(applicationContext) }

    private val viewModel: CaptureViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CaptureViewModel(repository) as T
            }
        }
    }

    private val prepareLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startVpn()
            else CaptureBus.setError("VPN permission denied")
        }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            val state by viewModel.captureState.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val transactions by viewModel.filteredTransactions.collectAsState()
            val storageLimit by settingsRepository.storageLimitMb
                .collectAsState(initial = SettingsRepository.DEFAULT_STORAGE_MB)
            val domainFilters by settingsRepository.domainFilters
                .collectAsState(initial = emptyList())

            // Keep the repository's exclusion list in sync with saved filters.
            repository.setExcludedHosts(domainFilters)

            val hooks = ScreenHooks(
                loadDetail = { id -> viewModel.transactionById(id)?.toDetailData() },
                loadDiagnostics = { diagnostics(state) },
                settings = SettingsHooks(
                    onInstallCert = ::installCertificate,
                    onExportCert = ::exportCertificate,
                    storageLimitMb = storageLimit,
                    onSetStorageLimitMb = { mb ->
                        lifecycleScope.launch { settingsRepository.setStorageLimitMb(mb) }
                    },
                    domainFilters = domainFilters,
                    onSetDomainFilters = { list ->
                        lifecycleScope.launch { settingsRepository.setDomainFilters(list) }
                    }
                )
            )

            MainScreen(
                isCapturing = state != CaptureState.STOPPED,
                searchQuery = searchQuery,
                transactions = transactions.map { it.toLogItem() },
                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onToggleCapture = ::toggleCapture,
                hooks = hooks
            )
        }
    }

    // ---- capture control ----------------------------------------------------

    private fun toggleCapture() {
        if (CaptureBus.state.value == CaptureState.STOPPED) {
            val consent = VpnService.prepare(this)
            if (consent != null) prepareLauncher.launch(consent) else startVpn()
        } else {
            stopVpn()
        }
    }

    private fun startVpn() {
        val intent = Intent(this, KniVpnService::class.java).setAction(KniVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopVpn() {
        val intent = Intent(this, KniVpnService::class.java).setAction(KniVpnService.ACTION_STOP)
        startService(intent)
    }

    // ---- certificate --------------------------------------------------------

    private fun installCertificate() {
        val cert = certManager.getX509()
        if (cert == null) {
            toast("Root CA is unavailable")
            return
        }
        try {
            val intent = KeyChain.createInstallIntent()
            intent.putExtra(KeyChain.EXTRA_CERTIFICATE, cert.encoded)
            intent.putExtra(KeyChain.EXTRA_NAME, "Kizuna Root CA")
            startActivity(intent)
        } catch (e: Exception) {
            toast("Cannot open certificate installer")
        }
    }

    private fun exportCertificate() {
        val file: File = certManager.getCertificateFile() ?: run {
            toast("Root CA is unavailable")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-pem-file"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Export Kizuna Root CA"))
        } catch (e: Exception) {
            toast("Export failed")
        }
    }

    // ---- diagnostics --------------------------------------------------------

    private fun diagnostics(state: CaptureState): List<Pair<String, String>> {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val dbBytes = File(KniPaths.db(this)).let { if (it.exists()) it.length() else 0L }
        val dbKb = dbBytes / 1024
        val packets = CaptureBus.packets.value
        val exchanges = repository.count()
        return listOf(
            "Capture State" to state.name,
            "Memory Usage" to "$usedMb MB",
            "Packets Processed" to packets.toString(),
            "Exchanges Recorded" to exchanges.toString(),
            "Database Size" to "$dbKb KB"
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

private fun NetworkTransaction.toLogItem(): LogItemData = LogItemData(
    id = id,
    method = method,
    url = url,
    status = status,
    duration = "${durationMs}ms",
    size = humanSize(reqSize + respSize)
)

private fun NetworkTransaction.toDetailData(): DetailData = DetailData(
    method = method,
    url = url,
    status = status,
    scheme = scheme,
    host = host,
    durationMs = durationMs,
    reqSize = reqSize,
    respSize = respSize,
    requestHeaders = requestHeaders,
    responseHeaders = responseHeaders,
    requestBody = requestBody,
    responseBody = responseBody
)

private fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
