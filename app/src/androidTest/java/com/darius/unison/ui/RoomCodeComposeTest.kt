package com.darius.unison.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.RoomUiState
import org.junit.Rule
import org.junit.Test

class RoomCodeComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun codeIsNotShownAutomatically() {
        showRoom(codeAvailable = true)

        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    @Test
    fun roomCodeOpensFromRoomMenu() {
        showRoom(codeAvailable = true)

        openRoomCode()

        composeRule.onNodeWithText(PIN).assertExists()
    }

    @Test
    fun manualDismissal() {
        showRoom(codeAvailable = true)
        openRoomCode()

        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    @Test
    fun backgroundHidesRoomCode() {
        showRoom(codeAvailable = true)
        openRoomCode()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    @Test
    fun memberWithoutLocalCredentialDoesNotSeeRoomCodeAction() {
        showRoom(codeAvailable = false)

        composeRule.onNodeWithContentDescription("Room actions").performClick()

        composeRule.onNodeWithText("Room code").assertDoesNotExist()
        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    private fun openRoomCode() {
        composeRule.onNodeWithContentDescription("Room actions").performClick()
        composeRule.onNodeWithText("Room code").performClick()
    }

    private fun showRoom(codeAvailable: Boolean) {
        composeRule.setContent {
            RoomScreen(
                state = roomState(codeAvailable),
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

    private fun roomState(codeAvailable: Boolean): MainUiState {
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
                    localRoomPin = PIN.takeIf { codeAvailable },
                )
        )
    }

    private companion object {
        const val PIN = "1234"
    }
}
