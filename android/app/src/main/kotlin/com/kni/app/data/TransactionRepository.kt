package com.kni.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransactionRepository {
    private val _transactions = MutableStateFlow<List<NetworkTransaction>>(emptyList())
    val transactions: StateFlow<List<NetworkTransaction>> = _transactions.asStateFlow()

    fun addTransaction(transaction: NetworkTransaction) {
        _transactions.value = listOf(transaction) + _transactions.value
    }

    fun clear() {
        _transactions.value = emptyList()
    }
}
