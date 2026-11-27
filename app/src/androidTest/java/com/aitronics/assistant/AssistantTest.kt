package com.aitronics.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class AssistantTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun send(command: String) {
        ui.onNode(hasSetTextAction()).performTextInput(command)
        ui.onNodeWithText("Send").performClick()
    }

    @Test fun memoryRoundTripFromChat() {
        send("remember shift at nine")
        ui.waitUntil(10000) { ui.onAllNodesWithText("Saved to local memory.").fetchSemanticsNodes().isNotEmpty() }
        send("recall memory")
        ui.waitUntil(10000) { ui.onAllNodesWithText("shift at nine", substring = true).filter(hasText("remember shift at nine").not()).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun notesSurviveActivityRecreation() {
        send("remember pilot run at noon")
        ui.waitUntil(10000) { ui.onAllNodesWithText("Saved to local memory.").fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        send("recall memory")
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("pilot run at noon", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
    @Test fun deviceCommandsWithoutCloudKey() {
        send("battery level")
        ui.onNodeWithText("Battery level:", substring = true, ignoreCase = false).assertExists()
        send("remind me tomorrow")
        ui.onNodeWithText("water the plants", substring = true).assertExists()
    }
}
