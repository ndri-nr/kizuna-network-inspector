package com.kni.ui.compose.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kni.ui.compose.theme.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    exchangeId: String,
    onBack: () -> Unit,
    loadDetail: (String) -> DetailData?
) {
    val detail = remember(exchangeId) { loadDetail(exchangeId) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Request", "Response", "Timing", "TLS")

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (detail != null) "${detail.method} ${detail.url}" else "Not found",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (detail != null) {
                            val statusText = if (detail.status == 0) "—" else detail.status.toString()
                            Text(
                                "Status: $statusText",
                                color = KniOnHeader.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (detail != null) {
                        TextButton(onClick = { copy("Full detail", detail.toMarkdown()) }) {
                            Text("Copy all", color = KniOnHeader)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KniHeader,
                    titleContentColor = KniOnHeader,
                    navigationIconContentColor = KniOnHeader,
                    actionIconContentColor = KniOnHeader
                )
            )
        },
        containerColor = KniBgPrimary
    ) { padding ->
        if (detail == null) {
            Box(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("Exchange not found.", color = KniTextSecondary)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = KniBgPrimary,
                contentColor = KniAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = KniAccent
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) KniAccent else KniTextSecondary
                            )
                        }
                    )
                }
            }

            // Per-tab copy affordance.
            val (tabLabel, tabMarkdown) = when (selectedTab) {
                0 -> "Request" to detail.requestMarkdown()
                1 -> "Response" to detail.responseMarkdown()
                2 -> "Timing" to detail.timingMarkdown()
                else -> "TLS" to detail.tlsMarkdown()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { copy("$tabLabel tab", tabMarkdown) }) {
                    Text("Copy tab", color = KniAccent, style = MaterialTheme.typography.labelMedium)
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    0 -> RequestTab(detail)
                    1 -> ResponseTab(detail)
                    2 -> TimingTab(detail)
                    3 -> TlsTab(detail)
                }
            }
        }
    }
}

private fun parseHeaders(json: String): List<Pair<String, String>> = try {
    val obj = JSONObject(json)
    obj.keys().asSequence().map { it to obj.optString(it) }.toList()
} catch (e: Exception) {
    emptyList()
}

@Composable
private fun RequestTab(detail: DetailData) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection("Request Headers", parseHeaders(detail.requestHeaders))
        BodySection("Request Body", detail.requestBody, detail.scheme)
    }
}

@Composable
private fun ResponseTab(detail: DetailData) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection("Response Headers", parseHeaders(detail.responseHeaders))
        BodySection("Response Body", detail.responseBody, detail.scheme)
    }
}

@Composable
fun HeaderSection(title: String, items: List<Pair<String, String>>) {
    Column {
        Text(
            title,
            color = KniAccent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("(none)", color = KniTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        items.forEach { (key, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "$key: ",
                    color = KniTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(value, color = KniTextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BodySection(title: String, rawBody: String, scheme: String) {
    val body = rawBody.ifBlank {
        if (scheme == "https")
            "(no body — HTTPS not decrypted for this exchange; enable Decrypt HTTPS in Settings and trust the CA)"
        else
            "(no body captured)"
    }
    Column {
        Text(
            title,
            color = KniAccent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = KniBgSurface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = body,
                modifier = Modifier.padding(16.dp),
                color = KniTextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TimingTab(detail: DetailData) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyVal("Duration", "${detail.durationMs} ms")
        KeyVal("Request size", "${detail.reqSize} bytes")
        KeyVal("Response size", "${detail.respSize} bytes")
    }
}

@Composable
private fun TlsTab(detail: DetailData) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KeyVal("Scheme", detail.scheme.uppercase())
        KeyVal("Host (SNI)", detail.host)
        if (detail.scheme == "https") {
            KeyVal("Version", detail.tlsVersion.ifBlank { "—" })
            KeyVal("Cipher", detail.tlsCipher.ifBlank { "—" })
            KeyVal("Certificate", detail.tlsCert.ifBlank { "—" })
            if (detail.tlsVersion.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Not decrypted. Enable Decrypt HTTPS in Settings and install/trust the Kizuna Root CA; apps that pin certificates cannot be intercepted.",
                    color = KniTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun KeyVal(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$key: ",
            color = KniTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        Text(value, color = KniTextPrimary, style = MaterialTheme.typography.bodySmall)
    }
}
