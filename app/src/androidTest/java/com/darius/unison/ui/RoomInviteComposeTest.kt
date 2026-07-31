package com.darius.unison.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.RoomUiState
import org.junit.Rule
import org.junit.Test

class RoomInviteComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun temporaryDisplayAfterCreation() {
        showRoom(host = true)

        composeRule.onNodeWithText(PIN).assertExists()
    }

    @Test
    fun automaticDismissal() {
        composeRule.mainClock.autoAdvance = false
        showRoom(host = true)
        composeRule.onNodeWithText(PIN).assertExists()

        composeRule.mainClock.advanceTimeBy(15_100L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    @Test
    fun manualDismissal() {
        showRoom(host = true)

        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    @Test
    fun reopeningThroughInvite() {
        showRoom(host = true)
        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithText("Invite").performClick()

        composeRule.onNodeWithText(PIN).assertExists()
    }

    @Test
    fun backgroundHidesInvite() {
        showRoom(host = true)
        composeRule.onNodeWithText(PIN).assertExists()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    @Test
    fun hostCanOpenInvite() {
        showRoom(host = true)

        composeRule.onNodeWithText("Invite").assertExists()
    }

    @Test
    fun joinedGuestNeverSeesInviteOrCode() {
        showRoom(host = false)

        composeRule.onNodeWithText("Invite").assertDoesNotExist()
        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    private fun showRoom(host: Boolean) {
        composeRule.setContent {
            RoomScreen(
                state = roomState(host),
                playbackPositionState = remember { mutableStateOf(0L) },
                onPlay = {},
                onPause = {},
                onSeek = {},
                onNext = {},
                onPrevious = {},
                onPlayQueueItem = {},
                onShuffle = {},
                onRepeat = {},
                onChooseFiles = {},
                onImportM3u = {},
                onAddAllMusicToRoom = {},
                onAddPlaylistToRoom = {},
                onRemoveQueueItem = {},
                onMoveQueueItem = { _, _ -> },
                onMoveQueueItemNext = {},
                onKeepTrack = {},
                onUpdateOptions = {},
                onSetRetentionPolicy = {},
                onSaveQueue = {},
                onClearPlayed = {},
                onClearQueue = {},
                onLeave = {},
            )
        }
    }

    private fun roomState(host: Boolean): MainUiState {
        val coordinator = PeerId("peer-000000000001")
        return MainUiState(
            room =
                RoomUiState(
                    snapshot =
                        RoomSnapshot(
                            roomId = "room",
                            roomName = "Room",
                            term = CoordinatorTerm(1L, coordinator),
                            sequence = 1L,
                        ),
                    isCoordinator = host,
                    // A guest must not reveal the value even if stale process state still contains
                    // it.
                    localRoomPin = PIN,
                )
        )
    }

    private companion object {
        const val PIN = "1234"
    }
}
