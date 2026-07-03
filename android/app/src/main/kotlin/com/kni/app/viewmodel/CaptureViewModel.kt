package com.kni.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kni.app.data.NetworkTransaction
import com.kni.app.data.TransactionRepository
import com.kni.platform.vpn.CaptureBus
import com.kni.platform.vpn.CaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class CaptureViewModel(private val repository: TransactionRepository) : ViewModel() {

    /** Mirrors the service's authoritative capture state. */
    val captureState: StateFlow<CaptureState> = CaptureBus.state
    val packets: StateFlow<Long> = CaptureBus.packets

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedMethods = MutableStateFlow<Set<String>>(emptySet())
    val selectedMethods: StateFlow<Set<String>> = _selectedMethods

    private val _selectedHosts = MutableStateFlow<Set<String>>(emptySet())
    val selectedHosts: StateFlow<Set<String>> = _selectedHosts

    val filteredTransactions: StateFlow<List<NetworkTransaction>> = combine(
        repository.transactions,
        _searchQuery
    ) { transactions, query ->
        if (query.isBlank()) {
            transactions
        } else {
            transactions.filter {
                it.url.contains(query, ignoreCase = true) ||
                    it.method.contains(query, ignoreCase = true) ||
                    it.host.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Poll the store for freshly captured exchanges.
        viewModelScope.launch {
            while (isActive) {
                repository.refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSelectedMethodsChanged(methods: Set<String>) {
        _selectedMethods.value = methods
    }

    fun onSelectedHostsChanged(hosts: Set<String>) {
        _selectedHosts.value = hosts
    }

    fun resetFilters() {
        _searchQuery.value = ""
        _selectedMethods.value = emptySet()
        _selectedHosts.value = emptySet()
    }

    fun transactionById(id: String): NetworkTransaction? = repository.getById(id)

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
