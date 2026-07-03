package com.kni.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import com.kni.ui.compose.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    isCapturing: Boolean,
    searchQuery: String,
    transactions: List<LogItemData>,
    selectedMethods: Set<String>,
    selectedHosts: Set<String>,
    onSearchQueryChanged: (String) -> Unit,
    onSelectedMethodsChanged: (Set<String>) -> Unit,
    onSelectedHostsChanged: (Set<String>) -> Unit,
    onResetFilters: () -> Unit,
    onDeleteTransactions: (List<String>) -> Unit,
    onClearAllTransactions: () -> Unit,
    onToggleCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    if (isSelectionMode) {
        BackHandler {
            selectedItemIds = emptySet()
            isSelectionMode = false
        }
    }

    val methods = remember(transactions) {
        transactions.map { it.method }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val hosts = remember(transactions) {
        transactions.map { it.host }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visible = transactions.filter {
        (selectedMethods.isEmpty() || it.method in selectedMethods) &&
            (selectedHosts.isEmpty() || it.host in selectedHosts)
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedItemIds.size} selected", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedItemIds = emptySet()
                            isSelectionMode = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteSelectedConfirm = true },
                            enabled = selectedItemIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = KniHeader,
                        titleContentColor = KniOnHeader,
                        navigationIconContentColor = KniOnHeader,
                        actionIconContentColor = KniOnHeader
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Live Capture", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        if (visible.isNotEmpty()) {
                            IconButton(onClick = { showDeleteAllConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete All", tint = KniOnHeader)
                            }
                        }
                        IconButton(onClick = onNavigateToDiagnostics) {
                            Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = KniHeader,
                        titleContentColor = KniOnHeader,
                        actionIconContentColor = KniOnHeader
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
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
            }
        },
        containerColor = KniBgPrimary
    ) { padding ->
        var showHostDialog by remember { mutableStateOf(false) }

        Column(modifier = Modifier.padding(padding)) {
            // Search Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = KniBgSurface,
                shape = RoundedCornerShape(24.dp)
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

            // Method multiselect chips + host filter (searchable, multiselect).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchQuery.isNotEmpty() || selectedMethods.isNotEmpty() || selectedHosts.isNotEmpty()) {
                    TextButton(
                        onClick = onResetFilters,
                        colors = ButtonDefaults.textButtonColors(contentColor = KniError),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Reset Filters", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", style = MaterialTheme.typography.labelMedium)
                    }
                }
                FilterChip(
                    selected = selectedHosts.isNotEmpty(),
                    onClick = { showHostDialog = true },
                    label = {
                        Text(if (selectedHosts.isEmpty()) "Host" else "Host (${selectedHosts.size})")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                methods.forEach { m ->
                    FilterChip(
                        selected = m in selectedMethods,
                        onClick = {
                            onSelectedMethodsChanged(
                                if (m in selectedMethods) selectedMethods - m else selectedMethods + m
                            )
                        },
                        label = { Text(m) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = methodColor(m).copy(alpha = 0.2f),
                            selectedLabelColor = methodColor(m)
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible.size) { index ->
                    val item = visible[index]
                    val isSelected = item.id in selectedItemIds
                    LogFeedItem(
                        method = item.method,
                        url = item.url,
                        host = item.host,
                        status = item.status,
                        duration = item.duration,
                        size = item.size,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                selectedItemIds = if (isSelected) {
                                    val newSet = selectedItemIds - item.id
                                    if (newSet.isEmpty()) isSelectionMode = false
                                    newSet
                                } else {
                                    selectedItemIds + item.id
                                }
                            } else {
                                onNavigateToDetail(item.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedItemIds = setOf(item.id)
                            }
                        }
                    )
                }
            }
        }

        if (showHostDialog) {
            MultiSelectSearchDialog(
                title = "Filter by Host",
                options = hosts,
                selected = selectedHosts,
                onDismiss = { showHostDialog = false },
                onConfirm = {
                    onSelectedHostsChanged(it)
                    showHostDialog = false
                }
            )
        }

        if (showDeleteAllConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAllConfirm = false },
                title = { Text("Delete all records?") },
                text = { Text("This will permanently clear all captured network traffic from the device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearAllTransactions()
                            showDeleteAllConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = KniError)
                    ) {
                        Text("Delete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteSelectedConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedConfirm = false },
                title = { Text("Delete selected records?") },
                text = { Text("Are you sure you want to delete the ${selectedItemIds.size} selected records?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteTransactions(selectedItemIds.toList())
                            selectedItemIds = emptySet()
                            isSelectionMode = false
                            showDeleteSelectedConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = KniError)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/** Searchable, multi-select picker over string [options]. Empty result = no filter. */
@Composable
fun MultiSelectSearchDialog(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val working = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    val shown = options.filter { it.contains(query, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (working.isNotEmpty()) {
                    TextButton(onClick = { working.clear() }) { Text("Clear (${working.size})") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown) { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (working.contains(opt)) working.remove(opt) else working.add(opt)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = working.contains(opt),
                                onCheckedChange = { checked ->
                                    if (checked) working.add(opt) else working.remove(opt)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(opt, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(working.toSet()) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogFeedItem(
    method: String,
    url: String,
    host: String,
    status: Int,
    duration: String,
    size: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) KniAccent.copy(alpha = 0.15f) else KniBgSurface
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(1.dp, KniAccent) else null,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
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
