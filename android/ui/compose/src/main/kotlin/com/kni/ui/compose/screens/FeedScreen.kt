package com.kni.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
        var methodFilter by remember { mutableStateOf<String?>(null) }
        var hostFilter by remember { mutableStateOf<String?>(null) }

        val methods = remember(transactions) {
            transactions.map { it.method }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val hosts = remember(transactions) {
            transactions.map { it.host }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val visible = transactions.filter {
            (methodFilter == null || it.method == methodFilter) &&
                (hostFilter == null || it.host == hostFilter)
        }

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

            // Host + method filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterDropdown(
                    label = "Method",
                    selected = methodFilter,
                    options = methods,
                    onSelect = { methodFilter = it },
                    modifier = Modifier.weight(1f)
                )
                FilterDropdown(
                    label = "Host",
                    selected = hostFilter,
                    options = hosts,
                    onSelect = { hostFilter = it },
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible.size) { index ->
                    val item = visible[index]
                    LogFeedItem(
                        method = item.method,
                        url = item.url,
                        host = item.host,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selected: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selected ?: "All ${label.lowercase()}s",
                color = if (selected != null) KniAccent else KniTextSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = KniTextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All ${label.lowercase()}s") },
                onClick = { onSelect(null); expanded = false }
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

data class LogItemData(
    val id: String,
    val method: String,
    val url: String,
    val host: String,
    val status: Int,
    val duration: String,
    val size: String
)

/** A distinct color per HTTP method so they're easy to tell apart in the feed. */
fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> Color(0xFF4CAF50)      // green
    "POST" -> Color(0xFF2196F3)     // blue
    "PUT" -> Color(0xFFFF9800)      // orange
    "PATCH" -> Color(0xFFFFC107)    // amber
    "DELETE" -> Color(0xFFF44336)   // red
    "HEAD" -> Color(0xFF9C27B0)     // purple
    "OPTIONS" -> Color(0xFF00BCD4)  // cyan
    "CONNECT" -> Color(0xFF795548)  // brown (HTTPS tunnel / not decrypted)
    else -> Color(0xFF9E9E9E)       // grey
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFeedItem(
    method: String,
    url: String,
    host: String,
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
            val mColor = methodColor(method)
            Surface(
                color = mColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = method,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = mColor,
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
                if (host.isNotBlank()) {
                    Text(
                        text = host,
                        color = KniTextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Row {
                    Text(
                        text = if (status == 0) "—" else status.toString(),
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
