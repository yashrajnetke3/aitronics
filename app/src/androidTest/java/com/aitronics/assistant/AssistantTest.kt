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
        inst.uiAutomation.executeShellCommand("appops set " + app.packageName + " SYSTEM_ALERT_WINDOW allow").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        val service = android.content.Intent(app,BackgroundWake::class.java)
        try {
            BackgroundWake.enable(app,true)
            ui.activityRule.scenario.recreate()

            ui.waitUntil(90000) { BackgroundWake.running && BackgroundWake.status.startsWith("Listening for") }
            inst.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            ui.waitUntil(10000) { BackgroundWake.foregroundCommand == null }
            ui.waitUntil(10000) { NeonBubble.overOtherApps }
            inst.runOnMainSync { NeonBubble.configure(app,true,false) }
            ui.waitUntil(5000) { !NeonBubble.overOtherApps }
            inst.runOnMainSync { NeonBubble.wake() }
            ui.waitUntil(5000) { NeonBubble.overOtherApps }
            ui.waitUntil(8000) { !NeonBubble.overOtherApps }
            inst.runOnMainSync { NeonBubble.configure(app,true,true) }
            org.junit.Assert.assertTrue(BackgroundWake.running)
            val manager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            org.junit.Assert.assertTrue(manager.activeNotifications.any { n -> n.notification.actions?.any { it.title == "Stop" } == true })
            manager.activeNotifications.first { it.notification.actions?.any { action -> action.title == "Stop" } == true }
                .notification.actions.first { it.title == "Stop" }.actionIntent.send()
            ui.waitUntil(10000) { !BackgroundWake.running && !BackgroundWake.enabled(app) }
            val reopen = requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName))
            reopen.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            inst.uiAutomation.executeShellCommand("am start -W -n " + requireNotNull(reopen.component).flattenToShortString())
                .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
            ui.waitUntil(15000) { BackgroundWake.foregroundCommand != null }
            ui.runOnIdle { org.junit.Assert.assertFalse(BackgroundWake.running) }
        } finally { BackgroundWake.enable(app,false); app.stopService(service) }
        ui.waitUntil(10000) { !BackgroundWake.running }
    }

    @Test fun shortWakeAndChatCommands() {
        org.junit.Assert.assertEquals("", WakeCommands.afterWake("hey", "hey aitronics"))
        org.junit.Assert.assertEquals("close camera", WakeCommands.afterWake("hey close camera", "hey aitronics"))
        org.junit.Assert.assertNull(WakeCommands.afterWake("they open camera", "hey aitronics"))
        org.junit.Assert.assertNull(WakeCommands.afterWake("hey", "hello computer"))
        org.junit.Assert.assertEquals("open Telegram", WakeCommands.normalize("open teligaram","hey aitronics"))
        org.junit.Assert.assertEquals("open WhatsApp", WakeCommands.normalize("open whatapp","hey aitronics"))
        for (q in listOf("close camera","scroll down","open chat with Sam","write message Good morning","read draft","send message","confirm send"))
            org.junit.Assert.assertTrue(ScreenCommands.handles(q))
        org.junit.Assert.assertFalse(ScreenCommands.handles("message 12345 saying Hi"))
        org.junit.Assert.assertEquals("scroll down in WhatsApp",WakeCommands.normalize("whatapp scroll dow","hey aitronics"))
        org.junit.Assert.assertTrue(ScreenCommands.followUp("close camera"))
        org.junit.Assert.assertFalse(ScreenCommands.followUp("confirm send"))
    }

    @Test fun bubbleDefaultsAndPersistentSwitch() {
        val app = ui.activity.applicationContext
        ui.runOnIdle {
            BackgroundWake.prefs(app).edit().remove("neonBubble").remove("pinNeonBubble").commit()
            NeonBubble.load(app)
        }
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithContentDescription("Neon wake bubble").assertIsOn().performClick()
        ui.onNodeWithContentDescription("Keep bubble visible").assertDoesNotExist()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithContentDescription("Neon wake bubble").assertIsOff()
        ui.runOnIdle { NeonBubble.wake(); org.junit.Assert.assertFalse(NeonBubble.awake) }
        ui.onNodeWithContentDescription("Neon wake bubble").performClick()
        ui.onNodeWithContentDescription("Keep bubble visible").assertIsOn()
        ui.runOnIdle { NeonBubble.wake(); org.junit.Assert.assertTrue(NeonBubble.awake); NeonBubble.configure(app,false,true); NeonBubble.configure(app,true,true) }
    }

    @Test fun composerReservesSpaceForSend() {
        ui.onNodeWithText("Send").assertIsDisplayed().assertIsNotEnabled()
        ui.onNode(hasSetTextAction()).performTextInput("battery level")
        ui.onNodeWithText("Send").assertIsEnabled()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        ui.onNodeWithText("Send",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertEquals(1,layouts.single().lineCount)
    }

    @Test fun deviceCommandsWithoutCloudKey() {
        send("battery level")
        waitForReply("Battery level:")
        send("remind me tomorrow")
        waitForReply("water the plants")
    }
}
