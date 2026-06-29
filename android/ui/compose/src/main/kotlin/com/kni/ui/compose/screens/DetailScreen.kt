package com.kni.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kni.ui.compose.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    exchangeId: String,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Headers", "Body", "Timing", "TLS")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GET https://api.kizuna.com/...", style = MaterialTheme.typography.titleMedium)
                        Text("Status: 200 OK", color = KniSuccess, style = MaterialTheme.typography.labelSmall)
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
                        text = { Text(title, color = if (selectedTab == index) KniAccent else KniTextSecondary) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    0 -> HeadersTab()
                    1 -> BodyTab()
                    2 -> Text("Timing Chart Placeholder", color = KniTextPrimary)
                    3 -> Text("TLS Details Placeholder", color = KniTextPrimary)
                }
            }
        }
    }
}

@Composable
fun HeadersTab() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeaderSection(title = "Request Headers", items = listOf("Accept" to "*/*", "Host" to "api.kizuna.com"))
        HeaderSection(title = "Response Headers", items = listOf("Content-Type" to "application/json", "Server" to "KizunaServer"))
    }
}

@Composable
fun HeaderSection(title: String, items: List<Pair<String, String>>) {
    Column {
        Text(title, color = KniAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        items.forEach { (key, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("$key: ", color = KniTextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(value, color = KniTextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun BodyTab() {
    Surface(
        color = KniBgSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "{\n  \"status\": \"success\",\n  \"data\": {\n    \"id\": 101,\n    \"message\": \"Hello from Kizuna!\"\n  }\n}",
            modifier = Modifier.padding(16.dp),
            color = KniTextPrimary,
            style = MaterialTheme.typography.bodySmall // In real app use Monospace
        )
    }
}
