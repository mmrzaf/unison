package com.darius.unison.app

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.darius.unison.model.DEFAULT_DISPLAY_NAME
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerId
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.room.port.IdentityStore
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun normalizeDisplayName(name: String): String =
    name.filterNot(Char::isISOControl).trim().take(MAX_DISPLAY_NAME_LENGTH).ifBlank {
        DEFAULT_DISPLAY_NAME
    }

private const val MAX_DISPLAY_NAME_LENGTH = 40

/**
 * Owns the process's single settings DataStore (one instance per file is a DataStore requirement;
 * [AppContainer] creates exactly one [UnisonSettings]).
 *
 * A corrupt or unreadable file is replaced by empty preferences, which yields a fresh random peer
 * identity and default settings. That is the correct recovery for local, non-secret settings, but
 * it is recorded so a changed identity is never unexplained.
 */
class UnisonSettings(context: Context, private val log: DiagnosticLog) : IdentityStore {
    private val identityMutex = Mutex()
    private val dataStore =
        PreferenceDataStoreFactory.create(
            corruptionHandler =
                ReplaceFileCorruptionHandler { error ->
                    log.warn(
                        TAG,
                        DiagnosticCategory.STORAGE,
                        "storage.settings.corruption_replaced",
                        throwable = error,
                    )
                    emptyPreferences()
                },
            produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
        )

    private object Keys {
        val peerId = stringPreferencesKey("peer_id")
        val displayName = stringPreferencesKey("display_name")
        val retention = stringPreferencesKey("retention_policy")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    private val data =
        dataStore.data.catch { error ->
            if (error !is IOException) throw error
            log.warn(
                TAG,
                DiagnosticCategory.STORAGE,
                "storage.settings.read_failed",
                throwable = error,
            )
            emit(emptyPreferences())
        }

    val identity: Flow<LocalIdentity> = data.map { prefs ->
        LocalIdentity(
            peerId = PeerId(prefs[Keys.peerId] ?: ""),
            displayName = normalizeDisplayName(prefs[Keys.displayName] ?: DEFAULT_DISPLAY_NAME),
        )
    }

    val onboardingComplete: Flow<Boolean> = data.map { it[Keys.onboardingComplete] ?: false }
    override val retentionPolicy: Flow<RetentionPolicy> = data.map { prefs ->
        runCatching {
                RetentionPolicy.valueOf(
                    prefs[Keys.retention] ?: RetentionPolicy.TEMPORARY_24_HOURS.name
                )
            }
            .getOrDefault(RetentionPolicy.TEMPORARY_24_HOURS)
    }

    override suspend fun ensureIdentity(): LocalIdentity = identityMutex.withLock {
        val prefs = data.first()
        val existing = prefs[Keys.peerId]?.takeIf(::isValidPeerId)
        if (existing != null)
            return@withLock LocalIdentity(
                PeerId(existing),
                normalizeDisplayName(prefs[Keys.displayName] ?: DEFAULT_DISPLAY_NAME),
            )
        createIdentity(prefs[Keys.displayName] ?: DEFAULT_DISPLAY_NAME)
    }

    /**
     * Replaces a duplicated installation identity while preserving the user's display name. This is
     * intentionally explicit: normal reconnects must keep the same peer ID.
     */
    override suspend fun rotateIdentity(): LocalIdentity = identityMutex.withLock {
        val displayName = data.first()[Keys.displayName] ?: DEFAULT_DISPLAY_NAME
        createIdentity(displayName)
    }

    private suspend fun createIdentity(displayName: String): LocalIdentity {
        val safeDisplayName = normalizeDisplayName(displayName)
        val id =
            ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") {
                "%02x".format(Locale.ROOT, it)
            }
        dataStore.edit {
            it[Keys.peerId] = id
            it[Keys.displayName] = safeDisplayName
        }
        return LocalIdentity(PeerId(id), safeDisplayName)
    }

    private fun isValidPeerId(value: String): Boolean =
        value.length in 16..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    override suspend fun saveDisplayName(name: String) {
        val safe = normalizeDisplayName(name)
        dataStore.edit {
            it[Keys.displayName] = safe
            it[Keys.onboardingComplete] = true
        }
    }

    suspend fun setRetentionPolicy(policy: RetentionPolicy) {
        dataStore.edit { it[Keys.retention] = policy.name }
    }

    private companion object {
        const val TAG = "UnisonSettings"
        const val FILE_NAME = "unison_settings"
    }
}
