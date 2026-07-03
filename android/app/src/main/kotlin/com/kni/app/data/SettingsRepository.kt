package com.kni.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val decryptHttpsKey = booleanPreferencesKey("decrypt_https")
    private val allowedAppsKey = stringPreferencesKey("allowed_apps")

    val storageLimitMb: Flow<Int> =
        context.settingsDataStore.data.map { it[storageLimitKey] ?: DEFAULT_STORAGE_MB }

    /** Hosts excluded from capture (substring match). */
    val domainFilters: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        (prefs[domainFiltersKey] ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** Whether HTTPS (443) traffic is decrypted via MITM. Off by default because
     *  MITM breaks apps that pin certificates or don't trust the user CA. */
    val decryptHttps: Flow<Boolean> =
        context.settingsDataStore.data.map { it[decryptHttpsKey] ?: false }

    /** Packages to capture exclusively. Empty = capture all apps on the device. */
    val allowedApps: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        (prefs[allowedAppsKey] ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    suspend fun setStorageLimitMb(mb: Int) {
        context.settingsDataStore.edit { it[storageLimitKey] = mb }
    }

    suspend fun setDomainFilters(hosts: List<String>) {
        context.settingsDataStore.edit { it[domainFiltersKey] = hosts.joinToString(",") }
    }

    suspend fun setDecryptHttps(enabled: Boolean) {
        context.settingsDataStore.edit { it[decryptHttpsKey] = enabled }
    }

    suspend fun setAllowedApps(packages: Set<String>) {
        context.settingsDataStore.edit { it[allowedAppsKey] = packages.joinToString(",") }
    }

    companion object {
        const val DEFAULT_STORAGE_MB = 1024
    }
}
