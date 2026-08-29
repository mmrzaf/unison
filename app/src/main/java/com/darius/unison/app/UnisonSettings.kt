package com.darius.unison.app

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerId
import com.darius.unison.model.RetentionPolicy
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore by
    preferencesDataStore(
        name = "unison_settings",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

class UnisonSettings(private val context: Context) {
    private val identityMutex = Mutex()

    private object Keys {
        val peerId = stringPreferencesKey("peer_id")
        val displayName = stringPreferencesKey("display_name")
        val retention = stringPreferencesKey("retention_policy")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    private val data =
        context.dataStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val identity: Flow<LocalIdentity> = data.map { prefs ->
        LocalIdentity(
            peerId = PeerId(prefs[Keys.peerId] ?: ""),
            displayName = prefs[Keys.displayName] ?: "Friend",
        )
    }

    val onboardingComplete: Flow<Boolean> = data.map { it[Keys.onboardingComplete] ?: false }
    val retentionPolicy: Flow<RetentionPolicy> = data.map { prefs ->
        runCatching {
            RetentionPolicy.valueOf(
                prefs[Keys.retention] ?: RetentionPolicy.TEMPORARY_24_HOURS.name
            )
        }.getOrDefault(RetentionPolicy.TEMPORARY_24_HOURS)
    }
    suspend fun ensureIdentity(): LocalIdentity = identityMutex.withLock {
        val prefs = data.first()
        val existing = prefs[Keys.peerId]?.takeIf(::isValidPeerId)
        if (existing != null)
            return@withLock LocalIdentity(
                PeerId(existing),
                prefs[Keys.displayName] ?: "Friend",
            )
        createIdentity(prefs[Keys.displayName] ?: "Friend")
    }

    /**
     * Replaces a duplicated installation identity while preserving the user's display name. This is
     * intentionally explicit: normal reconnects must keep the same peer ID.
     */
    suspend fun rotateIdentity(): LocalIdentity = identityMutex.withLock {
        val displayName = data.first()[Keys.displayName] ?: "Friend"
        createIdentity(displayName)
    }

    private suspend fun createIdentity(displayName: String): LocalIdentity {
        val id =
            ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") {
                "%02x".format(Locale.ROOT, it)
            }
        context.dataStore.edit { it[Keys.peerId] = id }
        return LocalIdentity(PeerId(id), displayName)
    }

    private fun isValidPeerId(value: String): Boolean =
        value.length in 16..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

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
