package com.darius.unison.network

import com.darius.unison.model.DiscoveredRoom

/**
 * De-duplicates rooms discovered during one user-triggered browse window.
 * The registry is cleared before every manual scan.
 */
class DiscoveredRoomRegistry {
    private val roomsByService = linkedMapOf<String, DiscoveredRoom>()

    @Synchronized
    fun clear() = roomsByService.clear()

    /** Returns true only when the visible room list changed. */
    @Synchronized
    fun found(room: DiscoveredRoom): Boolean {
        var changed = false
        val duplicateServices = roomsByService
            .filterValues { it.roomId == room.roomId && it.serviceName != room.serviceName }
            .keys
            .toList()
        duplicateServices.forEach {
            roomsByService.remove(it)
            changed = true
        }

        val previous = roomsByService.put(room.serviceName, room)
        return changed || previous != room
    }

    @Synchronized
    fun rooms(): List<DiscoveredRoom> = roomsByService.values.sortedWith { left, right ->
        val byName = left.roomName.compareTo(right.roomName, ignoreCase = true)
        if (byName != 0) byName else left.serviceName.compareTo(right.serviceName)
    }
}
