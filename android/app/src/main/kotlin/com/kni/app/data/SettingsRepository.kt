package com.kni.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "kni_settings")

/** Persisted user settings (Jetpack DataStore). */
class SettingsRepository(private val context: Context) {
    private val storageLimitKey = intPreferencesKey("storage_limit_mb")
    private val domainFiltersKey = stringPreferencesKey("domain_filters")

    val storageLimitMb: Flow<Int> =
        context.settingsDataStore.data.map { it[storageLimitKey] ?: DEFAULT_STORAGE_MB }

    /** Hosts excluded from capture (substring match). */
    val domainFilters: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        (prefs[domainFiltersKey] ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    suspend fun setStorageLimitMb(mb: Int) {
        context.settingsDataStore.edit { it[storageLimitKey] = mb }
    }

    suspend fun setDomainFilters(hosts: List<String>) {
        context.settingsDataStore.edit { it[domainFiltersKey] = hosts.joinToString(",") }
    }

    companion object {
        const val DEFAULT_STORAGE_MB = 1024
    }
}
