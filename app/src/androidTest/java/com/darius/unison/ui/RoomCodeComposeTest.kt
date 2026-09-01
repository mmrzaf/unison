package com.darius.unison.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.darius.unison.model.RoomLifecycleState
import org.junit.Rule
import org.junit.Test

class RoomCodeComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun roomHeaderShowsPlainNumericCodeWithoutCopyUi() {
        composeRule.setContent {
            RoomHeader(
                roomName = "Darius's room",
                roomCode = PIN,
                connectedListeners = 1,
                lifecycle = RoomLifecycleState.CONNECTED,
                onShowListeners = {},
                onShowLogs = {},
                onShowAbout = {},
                onLeave = {},
            )
        }

        composeRule.onNodeWithText(PIN).assertExists()
        composeRule.onNodeWithText("Room code").assertDoesNotExist()
        composeRule.onNodeWithText("Copy").assertDoesNotExist()
    }

    private companion object {
        const val PIN = "1234"
    }
}
