package com.kni.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kni.ui.compose.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, hooks: SettingsHooks) {
    var showStorageDialog by remember { mutableStateOf(false) }
    var showFiltersDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

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
                description = "Save the Kizuna Root CA to a file, then install it via " +
                    "Settings → Security → Install a certificate → CA certificate. " +
                    "(The in-app Install button is unreliable on newer Android.)",
                actionText = "Save to File",
                onClick = hooks.onSaveCert,
                secondaryText = "Install",
                onSecondary = hooks.onInstallCert,
                tertiaryText = "Share",
                onTertiary = hooks.onExportCert
            )
            SettingsSwitch(
                title = "Decrypt HTTPS (MITM)",
                description = "Terminate and decrypt HTTPS to read request/response bodies. " +
                    "Requires the Root CA to be installed and trusted. Apps that pin " +
                    "certificates (e.g. Instagram, WhatsApp) cannot be decrypted and may " +
                    "fail to connect — scope capture to your target app below.",
                checked = hooks.decryptHttps,
                onCheckedChange = hooks.onSetDecryptHttps
            )
            SettingsItem(
                title = "Apps to Capture",
                description = if (hooks.selectedApps.isEmpty())
                    "Capturing all apps. Select specific apps to reduce noise and avoid breaking pinned apps."
                else
                    "Capturing ${hooks.selectedApps.size} app(s). Only selected apps are routed through the VPN.",
                actionText = "Choose Apps",
                onClick = { showAppPicker = true }
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

    if (showAppPicker) {
        AppPickerDialog(
            apps = hooks.installedApps,
            selected = hooks.selectedApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { set ->
                hooks.onSetSelectedApps(set)
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppInfo>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val working = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apps to Capture") },
        text = {
            Column {
                Text(
                    "None selected = capture all apps.",
                    color = KniTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(apps) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = working.contains(app.packageName),
                                onCheckedChange = { checked ->
                                    if (checked) working.add(app.packageName)
                                    else working.remove(app.packageName)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.packageName,
                                    color = KniTextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(working.toSet()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = KniTextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(description, color = KniTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    tertiaryText: String? = null,
    onTertiary: (() -> Unit)? = null,
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
            if (tertiaryText != null && onTertiary != null) {
                OutlinedButton(onClick = onTertiary) { Text(tertiaryText) }
            }
        }
    }
}
