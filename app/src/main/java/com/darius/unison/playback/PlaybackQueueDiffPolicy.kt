package com.darius.unison.playback

/** Chooses between safe incremental timeline edits and one bounded rebuild for large mutations. */
internal object PlaybackQueueDiffPolicy {
    const val DEFAULT_MAX_INCREMENTAL_CHANGES = 32

    fun shouldRebuild(
        currentIds: List<String>,
        desiredIds: List<String>,
        maxIncrementalChanges: Int = DEFAULT_MAX_INCREMENTAL_CHANGES,
    ): Boolean {
        require(maxIncrementalChanges >= 0)
        if (currentIds.isEmpty()) return desiredIds.size > maxIncrementalChanges
        if (desiredIds.isEmpty()) return false
        if (currentIds == desiredIds) return false

        val currentSet = currentIds.toHashSet()
        val desiredSet = desiredIds.toHashSet()
        var changes =
            currentIds.count { it !in desiredSet } + desiredIds.count { it !in currentSet }
        if (changes > maxIncrementalChanges) return true

        val sharedCurrent = currentIds.filter(desiredSet::contains)
        val sharedDesired = desiredIds.filter(currentSet::contains)
        val comparable = minOf(sharedCurrent.size, sharedDesired.size)
        for (index in 0 until comparable) {
            if (sharedCurrent[index] != sharedDesired[index] && ++changes > maxIncrementalChanges)
                return true
        }
        return false
    }
}
