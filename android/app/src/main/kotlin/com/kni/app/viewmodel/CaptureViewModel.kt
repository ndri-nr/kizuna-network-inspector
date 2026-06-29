package com.kni.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kni.app.data.NetworkTransaction
import com.kni.app.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CaptureViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredTransactions: StateFlow<List<NetworkTransaction>> = combine(
        repository.transactions,
        _searchQuery
    ) { transactions, query ->
        if (query.isBlank()) {
            transactions
        } else {
            transactions.filter { 
                it.url.contains(query, ignoreCase = true) || 
                it.method.contains(query, ignoreCase = true) 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleCapture() {
        _isCapturing.value = !_isCapturing.value
        // Logic to start/stop VpnService will be triggered by activity observing this
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
