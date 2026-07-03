package com.kni.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kni.ui.compose.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, hooks: SettingsHooks) {
    var showStorageDialog by remember { mutableStateOf(false) }
    var showFiltersDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsItem(
                title = "Root Certificate",
                description = "Install and trust the Kizuna Root CA for HTTPS inspection.",
                actionText = "Install",
                onClick = hooks.onInstallCert,
                secondaryText = "Export .crt",
                onSecondary = hooks.onExportCert
            )
            SettingsItem(
                title = "Storage Limit",
                description = "Maximum database size for captured traffic (currently ${hooks.storageLimitMb} MB).",
                actionText = "Configure",
                onClick = { showStorageDialog = true }
            )
            SettingsItem(
                title = "Domain Filters",
                description = if (hooks.domainFilters.isEmpty())
                    "Exclude specific domains from being captured. (none set)"
                else
                    "Excluded: ${hooks.domainFilters.joinToString(", ")}",
                actionText = "Manage",
                onClick = { showFiltersDialog = true }
            )
            SettingsItem(
                title = "Background Protection",
                description = if (hooks.isBatteryOptimized)
                    "Battery optimization is active. The capture service might be killed when running in the background."
                else
                    "Protected: Battery optimization is disabled. Kizuna will run reliably in the background.",
                actionText = if (hooks.isBatteryOptimized) "Configure" else "Protected",
                onClick = hooks.onRequestIgnoreBatteryOptimizations,
                enabled = hooks.isBatteryOptimized
            )
        }
    }

    if (showStorageDialog) {
        TextEditDialog(
            title = "Storage Limit (MB)",
            initial = hooks.storageLimitMb.toString(),
            onDismiss = { showStorageDialog = false },
            onConfirm = { value ->
                value.toIntOrNull()?.let { hooks.onSetStorageLimitMb(it) }
                showStorageDialog = false
            }
        )
    }

    if (showFiltersDialog) {
        TextEditDialog(
            title = "Domain Filters (comma-separated)",
            initial = hooks.domainFilters.joinToString(", "),
            onDismiss = { showFiltersDialog = false },
            onConfirm = { value ->
                val list = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                hooks.onSetDomainFilters(list)
                showFiltersDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettingsItem(
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Column {
        Text(title, color = KniTextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(description, color = KniTextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KniAccent,
                    disabledContainerColor = KniTextSecondary.copy(alpha = 0.2f)
                )
            ) {
                Text(actionText, color = if (enabled) KniTextPrimary else KniTextSecondary)
            }
            if (secondaryText != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary) { Text(secondaryText) }
            }
        }
    }
}
