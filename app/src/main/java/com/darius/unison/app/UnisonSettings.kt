package com.darius.unison.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerId
import com.darius.unison.model.RetentionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.dataStore by preferencesDataStore(name = "unison_settings")

class UnisonSettings(private val context: Context) {
    private object Keys {
        val peerId = stringPreferencesKey("peer_id")
        val displayName = stringPreferencesKey("display_name")
        val retention = stringPreferencesKey("retention_policy")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    val identity: Flow<LocalIdentity> = context.dataStore.data.map { prefs ->
        LocalIdentity(
            peerId = PeerId(prefs[Keys.peerId] ?: ""),
            displayName = prefs[Keys.displayName] ?: "Friend",
        )
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingComplete] ?: false }
    val retentionPolicy: Flow<RetentionPolicy> = context.dataStore.data.map {
        runCatching { RetentionPolicy.valueOf(it[Keys.retention] ?: RetentionPolicy.TEMPORARY_24_HOURS.name) }
            .getOrDefault(RetentionPolicy.TEMPORARY_24_HOURS)
    }

    suspend fun ensureIdentity(): LocalIdentity {
        val prefs = context.dataStore.data.first()
        val existing = prefs[Keys.peerId]
        if (existing != null) return LocalIdentity(PeerId(existing), prefs[Keys.displayName] ?: "Friend")
        val id = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        context.dataStore.edit { it[Keys.peerId] = id }
        return LocalIdentity(PeerId(id), prefs[Keys.displayName] ?: "Friend")
    }

    suspend fun saveDisplayName(name: String) {
        val safe = name.trim().take(40).ifBlank { "Friend" }
        context.dataStore.edit {
            it[Keys.displayName] = safe
            it[Keys.onboardingComplete] = true
        }
    }

    suspend fun setRetentionPolicy(policy: RetentionPolicy) {
        context.dataStore.edit { it[Keys.retention] = policy.name }
    }
}
