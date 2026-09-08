package com.agent.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("agent_settings")

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 8787,
    val pairingCode: String = "",
    val token: String = "",
    val sessionId: String = "",
)

class SettingsRepository(private val context: Context) {
    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")
    private val pairingKey = stringPreferencesKey("pairing_code")
    private val sessionKey = stringPreferencesKey("session_id")

    private val encrypted = EncryptedSharedPreferences.create(
        context,
        "agent_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val flow: Flow<ConnectionSettings> = context.settingsStore.data.map { prefs ->
        ConnectionSettings(
            host = prefs[hostKey].orEmpty(),
            port = prefs[portKey] ?: 8787,
            pairingCode = prefs[pairingKey].orEmpty(),
            token = encrypted.getString(TOKEN_KEY, "").orEmpty(),
            sessionId = prefs[sessionKey].orEmpty(),
        )
    }

    suspend fun snapshot(): ConnectionSettings = flow.first()

    suspend fun update(
        host: String? = null,
        port: Int? = null,
        pairingCode: String? = null,
        token: String? = null,
        sessionId: String? = null,
    ) {
        context.settingsStore.edit { prefs ->
            host?.let { prefs[hostKey] = it.trim() }
            port?.let { prefs[portKey] = it }
            pairingCode?.let { prefs[pairingKey] = it.trim() }
            sessionId?.let { prefs[sessionKey] = it }
        }
        if (token != null) {
            encrypted.edit().putString(TOKEN_KEY, token).apply()
        }
    }

    suspend fun clearSession() {
        update(token = "", sessionId = "")
    }

    companion object {
        private const val TOKEN_KEY = "device_token"
    }
}
