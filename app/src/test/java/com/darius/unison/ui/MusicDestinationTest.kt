package com.darius.unison.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDestinationTest {
    @Test
    fun playlistPlacementKeepsMusicInLibrary() {
        val destination = MusicDestination(saveToLibrary = false, playlistIds = setOf("road-trip"))

        assertTrue(destination.keepsInLibrary)
        assertTrue(destination.hasDestination)
    }

    @Test
    fun newPlaylistPlacementKeepsMusicInLibrary() {
        val destination = MusicDestination(saveToLibrary = false, newPlaylistName = "Gym")

        assertTrue(destination.keepsInLibrary)
        assertTrue(destination.hasDestination)
    }

    @Test
    fun roomOnlyImportRemainsValidWithoutPermanentLibraryRetention() {
        val destination = MusicDestination(saveToLibrary = false, addToRoom = true)

        assertFalse(destination.keepsInLibrary)
        assertTrue(destination.hasDestination)
    }

    @Test
    fun emptyDestinationIsRejected() {
        val destination = MusicDestination(saveToLibrary = false)

        assertFalse(destination.keepsInLibrary)
        assertFalse(destination.hasDestination)
    }
}
