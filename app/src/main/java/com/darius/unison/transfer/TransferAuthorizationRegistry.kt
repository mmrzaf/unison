package com.darius.unison.transfer

import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.Crypto
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded, expiring, one-use authorization registry for incoming file transfers.
 *
 * Lookup does not consume an authorization because the caller must first verify the request proof.
 * [consume] uses compare-and-remove so concurrent replay attempts cannot both succeed.
 */
internal class TransferAuthorizationRegistry(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowElapsedMs: () -> Long,
    private val onCapacityEviction: () -> Unit = {},
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    data class Authorization(
        val roomId: String,
        val trackId: TrackId,
        val destinationPeerId: PeerId,
        val token: String,
        val expiresAtElapsedMs: Long,
    )

    private val entries = ConcurrentHashMap<String, Authorization>()

    val size: Int
        get() = entries.size

    @Synchronized
    fun authorize(
        roomId: String,
        trackId: TrackId,
        destinationPeerId: PeerId,
        token: String,
        expiresAtElapsedMs: Long,
    ): String {
        require(roomId.isNotBlank()) { "roomId must not be blank" }
        require(token.isNotBlank()) { "token must not be blank" }
        pruneExpired()
        val authorizationId = Crypto.fileTransferAuthorizationId(token)
        if (entries.size >= maxEntries && !entries.containsKey(authorizationId)) {
            entries.entries
                .minByOrNull { it.value.expiresAtElapsedMs }
                ?.let { eldest ->
                    if (entries.remove(eldest.key, eldest.value)) onCapacityEviction()
                }
        }
        entries[authorizationId] =
            Authorization(
                roomId = roomId,
                trackId = trackId,
                destinationPeerId = destinationPeerId,
                token = token,
                expiresAtElapsedMs = expiresAtElapsedMs,
            )
        return authorizationId
    }

    fun findMatching(
        authorizationId: String,
        roomId: String,
        trackId: TrackId,
        destinationPeerId: PeerId,
    ): Authorization? {
        val authorization = entries[authorizationId] ?: return null
        if (authorization.expiresAtElapsedMs <= nowElapsedMs()) {
            entries.remove(authorizationId, authorization)
            return null
        }
        return authorization.takeIf {
            it.roomId == roomId &&
                it.trackId == trackId &&
                it.destinationPeerId == destinationPeerId
        }
    }

    /** Returns true only for the first caller consuming this exact authorization instance. */
    fun consume(authorizationId: String, authorization: Authorization): Boolean =
        entries.remove(authorizationId, authorization)

    fun clear() {
        entries.clear()
    }

    @Synchronized
    private fun pruneExpired() {
        val now = nowElapsedMs()
        entries.entries.removeIf { it.value.expiresAtElapsedMs <= now }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 512
    }
}
