package com.kni.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kni.ui.compose.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
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
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SettingsItem(
                title = "Root Certificate",
                description = "Install and trust Kizuna Root CA for HTTPS inspection.",
                actionText = "Install"
            )
            SettingsItem(
                title = "Storage Limit",
                description = "Maximum database size for captured traffic (currently 1GB).",
                actionText = "Configure"
            )
            SettingsItem(
                title = "Domain Filters",
                description = "Exclude specific domains from being captured.",
                actionText = "Manage"
            )
        }
    }
}

@Composable
fun SettingsItem(title: String, description: String, actionText: String) {
    Column {
        Text(title, color = KniTextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(description, color = KniTextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = KniAccent)) {
            Text(actionText)
        }
    }
}
