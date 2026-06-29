package com.kni.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kni.app.data.TransactionRepository
import com.kni.app.viewmodel.CaptureViewModel
import com.kni.ui.compose.MainScreen
import com.kni.ui.compose.screens.LogItemData

class MainActivity : ComponentActivity() {
    private val repository = TransactionRepository()
    private val viewModel: CaptureViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CaptureViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isCapturing by viewModel.isCapturing.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val transactions by viewModel.filteredTransactions.collectAsState()

            MainScreen(
                isCapturing = isCapturing,
                searchQuery = searchQuery,
                transactions = transactions.map { 
                    LogItemData(it.id, it.method, it.url, it.status, it.duration, it.size) 
                },
                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onToggleCapture = { 
                    viewModel.toggleCapture()
                    handleCaptureToggle(viewModel.isCapturing.value)
                }
            )
        }
    }

    private fun handleCaptureToggle(capturing: Boolean) {
        val intent = Intent(this, com.kni.platform.vpn.KniVpnService::class.java)
        if (capturing) {
            intent.action = "START_VPN"
            startService(intent)
        } else {
            intent.action = "STOP_VPN"
            stopService(intent)
        }
    }
}
