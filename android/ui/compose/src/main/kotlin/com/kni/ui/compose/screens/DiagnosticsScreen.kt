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
fun DiagnosticsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
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
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DiagnosticCard(label = "Memory Usage", value = "42 MB")
            DiagnosticCard(label = "CPU Load", value = "4%")
            DiagnosticCard(label = "Packets Processed", value = "1,204")
            DiagnosticCard(label = "Database Size", value = "8.4 MB")
        }
    }
}

@Composable
fun DiagnosticCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KniBgSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = KniTextSecondary)
            Text(value, color = KniAccent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}
