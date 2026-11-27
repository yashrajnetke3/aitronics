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

    private fun waitForReply(text: String) {
        try {
            ui.waitUntil(15000) {
                ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            throw AssertionError("Expected reply containing '$text'. UI: " + ui.onRoot().printToString(), error)
        }
    }

    @Test fun memoryRoundTripFromChat() {
        send("remember shift at nine")
        waitForReply("Saved to local memory.")
        send("recall memory")
        ui.waitUntil(10000) { ui.onAllNodesWithText("shift at nine", substring = true).filter(hasText("remember shift at nine").not()).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun notesSurviveActivityRecreation() {
        send("remember pilot run at noon")
        waitForReply("Saved to local memory.")
        ui.activityRule.scenario.recreate()
        send("recall memory")
        waitForReply("pilot run at noon")
    }
    @Test fun deviceCommandsWithoutCloudKey() {
        send("battery level")
        waitForReply("Battery level:")
        send("remind me tomorrow")
        waitForReply("water the plants")
    }
}
