package com.darius.unison.library

/** In-memory reference counting for temporary persisted SAF grants. */
class UriPermissionLedger {
    private val owners = LinkedHashMap<String, Int>()

    @Synchronized
    fun acquire(uri: String): Boolean {
        val previous = owners[uri] ?: 0
        owners[uri] = previous + 1
        return previous == 0
    }

    @Synchronized
    fun release(uri: String): Boolean {
        val previous = owners[uri] ?: return false
        return if (previous <= 1) {
            owners.remove(uri)
            true
        } else {
            owners[uri] = previous - 1
            false
        }
    }

    @Synchronized fun isActive(uri: String): Boolean = (owners[uri] ?: 0) > 0

    @Synchronized fun activeUris(): Set<String> = owners.keys.toSet()
}
