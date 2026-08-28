package com.darius.unison.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class RoomCodeComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun roomCodeDialogDisplaysTheCode() {
        showRoomCode()

        composeRule.onNodeWithText(PIN).assertExists()
        composeRule.onNodeWithText("Room code").assertExists()
    }

    @Test
    fun doneDismissesRoomCodeDialog() {
        showRoomCode()

        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithText(PIN).assertDoesNotExist()
    }

    private fun showRoomCode() {
        composeRule.setContent {
            var shown by remember { mutableStateOf(true) }
            if (shown) RoomCodeDialog(roomCode = PIN, onDismiss = { shown = false })
        }
    }

    private companion object {
        const val PIN = "1234"
    }
}
