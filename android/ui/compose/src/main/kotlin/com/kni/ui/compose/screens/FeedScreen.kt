package com.kni.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kni.ui.compose.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    isCapturing: Boolean,
    searchQuery: String,
    transactions: List<LogItemData>,
    onSearchQueryChanged: (String) -> Unit,
    onToggleCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Capture", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KniBgPrimary,
                    titleContentColor = KniTextPrimary,
                    actionIconContentColor = KniTextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onToggleCapture,
                containerColor = if (isCapturing) KniError else KniAccent,
                contentColor = Color.White
            ) {
                Icon(
                    if (isCapturing) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = if (isCapturing) "Stop" else "Start"
                )
            }
        },
        containerColor = KniBgPrimary
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = KniBgSurface,
                shape = RoundedCornerShape(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search traffic...", color = KniTextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = KniTextSecondary) },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions.size) { index ->
                    val item = transactions[index]
                    LogFeedItem(
                        method = item.method,
                        url = item.url,
                        status = item.status,
                        duration = item.duration,
                        size = item.size,
                        onClick = { onNavigateToDetail(item.id) }
                    )
                }
            }
        }
    }
}

data class LogItemData(
    val id: String,
    val method: String,
    val url: String,
    val status: Int,
    val duration: String,
    val size: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFeedItem(
    method: String,
    url: String,
    status: Int,
    duration: String,
    size: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = KniBgSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Method Badge
            Surface(
                color = if (method == "GET") KniSuccess.copy(alpha = 0.2f) else KniAccent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = method,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (method == "GET") KniSuccess else KniAccent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = url,
                    color = KniTextPrimary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    maxLines = 1
                )
                Row {
                    Text(
                        text = "$status OK",
                        color = KniSuccess,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$duration • $size",
                        color = KniTextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
