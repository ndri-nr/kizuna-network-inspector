package com.kni.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val tabs = listOf("Headers", "Body", "Timing", "TLS")

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
                                color = KniSuccess,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KniBgPrimary,
                    titleContentColor = KniTextPrimary,
                    navigationIconContentColor = KniTextPrimary
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

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    0 -> HeadersTab(detail)
                    1 -> BodyTab(detail)
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
private fun HeadersTab(detail: DetailData) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection("Request Headers", parseHeaders(detail.requestHeaders))
        HeaderSection("Response Headers", parseHeaders(detail.responseHeaders))
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
private fun BodyTab(detail: DetailData) {
    val body = detail.responseBody.ifBlank {
        detail.requestBody.ifBlank {
            if (detail.scheme == "https")
                "Body is encrypted (HTTPS). Decryption arrives in Phase 2 (MITM)."
            else
                "(no body captured)"
        }
    }
    Surface(
        color = KniBgSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = body,
            modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            color = KniTextPrimary,
            style = MaterialTheme.typography.bodySmall
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyVal("Scheme", detail.scheme.uppercase())
        KeyVal("Host (SNI)", detail.host)
        if (detail.scheme == "https") {
            Text(
                "TLS is intercepted at the tunnel; payload is encrypted. Decryption (per-host leaf certs) arrives in Phase 2.",
                color = KniTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
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
