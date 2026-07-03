package com.kni.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kni.ui.compose.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    loadDiagnostics: () -> List<Pair<String, String>>
) {
    // Refresh live metrics on a timer while the screen is visible.
    var metrics by remember { mutableStateOf(loadDiagnostics()) }
    LaunchedEffect(Unit) {
        while (true) {
            metrics = loadDiagnostics()
            delay(1000)
        }
    }

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
                    containerColor = KniHeader,
                    titleContentColor = KniOnHeader,
                    navigationIconContentColor = KniOnHeader
                )
            )
        },
        containerColor = KniBgPrimary
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            metrics.forEach { (label, value) ->
                DiagnosticCard(label = label, value = value)
            }
        }
    }
}

@Composable
fun DiagnosticCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KniBgSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = KniTextSecondary)
            Text(
                value,
                color = KniAccent,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}
