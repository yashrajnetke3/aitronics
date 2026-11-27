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
    @Test fun spokenAliases() {
        org.junit.Assert.assertEquals("turn on flashlight",WakeCommands.normalize("hay open light","hey aitronics"))
        org.junit.Assert.assertEquals("open camera",WakeCommands.normalize("electronics open camera","hey aitronics"))
        org.junit.Assert.assertEquals("open camera",WakeCommands.afterWake("hey electronics open camera","hey aitronics"))
        org.junit.Assert.assertNull(WakeCommands.afterWake("open camera","hey aitronics"))
    }

    @Test fun wakeContinuesAfterHomeAndStopsOnRequest() {
        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext
        val permissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions += android.Manifest.permission.POST_NOTIFICATIONS
        for (permission in permissions) inst.uiAutomation.grantRuntimePermission(app.packageName, permission)
        val service = android.content.Intent(app,BackgroundWake::class.java)
        try {
            ui.runOnIdle { ui.activity.startForegroundService(service) }
            ui.waitUntil(90000) { BackgroundWake.running && BackgroundWake.status.startsWith("Listening for") }
            inst.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            ui.waitUntil(10000) { BackgroundWake.foregroundCommand == null }
            org.junit.Assert.assertTrue(BackgroundWake.running)
            val manager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            org.junit.Assert.assertTrue(manager.activeNotifications.any { n -> n.notification.actions?.any { it.title == "Stop" } == true })
        } finally { app.stopService(service) }
        ui.waitUntil(10000) { !BackgroundWake.running }
    }

    @Test fun deviceCommandsWithoutCloudKey() {
        send("battery level")
        waitForReply("Battery level:")
        send("remind me tomorrow")
        waitForReply("water the plants")
    }
}
