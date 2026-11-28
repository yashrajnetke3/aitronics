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

    private fun assertMemoryVisible(note: String) {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.waitUntil(10000) {
            automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(note)?.isNotEmpty() == true
        }
    }

    @Test fun cancellingDocumentPickerLeavesNoteIntact() {
        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val dao = MemoryDatabase.get(inst.targetContext).notes()
        val body = "export cancellation " + java.util.UUID.randomUUID()
        dao.add(Memory(note = body))
        val note = dao.records().first { NoteCipher.decrypt(it.note) == body }
        try {
            send("export memory " + note.id)
            ui.waitUntil(10000) {
                inst.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Choose destination")
                    ?.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            ui.waitUntil(10000) {
                val pkg = inst.uiAutomation.rootInActiveWindow?.packageName?.toString()
                pkg != null && pkg != inst.targetContext.packageName
            }
            inst.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            waitForReply("Export cancelled.")
            org.junit.Assert.assertEquals(note, dao.snapshot(note.id))
        } finally { dao.deleteIfCurrent(note.id, note.note) }
    }

    @Test fun memoryRoundTripFromChat() {
        send("remember shift at nine")
        waitForReply("Saved to local memory.")
        send("recall memory")
        assertMemoryVisible("shift at nine")
    }
    @Test fun notesSurviveActivityRecreation() {
        send("remember pilot run at noon")
        waitForReply("Saved to local memory.")
        ui.activityRule.scenario.recreate()
        send("recall memory")
        assertMemoryVisible("pilot run at noon")
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
            inst.runOnMainSync { BackgroundWake.finalTranscript("hey aitronics") }
            ui.waitUntil(5000) { NeonBubble.overOtherApps && NeonBubble.awake && NeonBubble.listening }
            inst.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/aitronics-wake.png").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            inst.runOnMainSync { BackgroundWake.finalTranscript("battery level") }
            ui.waitUntil(5000) { NeonBubble.awake && !NeonBubble.listening && BackgroundWake.status.startsWith("Battery level:") }
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

    @Test fun bundledSpeechModelRoutesAudio() {
        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val directory = java.nio.file.Files.createTempDirectory(inst.targetContext.cacheDir.toPath(), "voice-test").toFile()
        try {
            java.util.zip.ZipInputStream(inst.targetContext.assets.open("wake-en-015.zip")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val file = java.io.File(directory, entry.name).canonicalFile
                        require(file.path.startsWith(directory.canonicalPath + java.io.File.separator))
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val phrase = "hey aitronics"
            org.vosk.Model(java.io.File(directory, "vosk-model-small-en-us-0.15").path).use { model ->
                fun recognize(name: String): String {
                    val words = mutableListOf<String>()
                    org.vosk.Recognizer(model, 16000f).use { recognizer ->
                        java.util.zip.GZIPInputStream(inst.context.assets.open("voice/$name.clip")).use { input ->
                            val buffer = ByteArray(3200)
                            while (true) {
                                var count = 0
                                while (count < buffer.size) {
                                    val read = input.read(buffer, count, buffer.size - count)
                                    if (read < 0) break
                                    count += read
                                }
                                if (count == 0) break
                                if (recognizer.acceptWaveForm(buffer, count)) {
                                    words += org.json.JSONObject(recognizer.result).optString("text")
                                }
                            }
                        }
                        words += org.json.JSONObject(recognizer.finalResult).optString("text")
                    }
                    return words.filter(String::isNotBlank).joinToString(" ")
                }
                for (name in listOf("long-wake", "short-wake")) {
                    val heard = recognize(name)
                    org.junit.Assert.assertEquals("$name decoded as $heard", "", WakeCommands.afterWake(heard, phrase))
                }
                for ((name, expected) in mapOf("camera" to "open camera", "close" to "close camera", "battery" to "battery level")) {
                    val heard = recognize(name)
                    org.junit.Assert.assertEquals("$name decoded as $heard", expected, WakeCommands.normalizeSpeech(heard, phrase))
                }
                org.junit.Assert.assertNull(WakeCommands.afterWake(recognize("negative"), phrase))
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun openCameraReachesAnInstalledCameraOrExplainsItsAbsence() {
        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val camera = android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val candidates = inst.targetContext.packageManager.queryIntentActivities(camera, 0)
            .map { it.activityInfo.packageName }.toSet()
        send("Hey AITRONICS open camera")
        if (candidates.isEmpty()) {
            waitForReply("No compatible application found")
            return
        }
        try {
            ui.waitUntil(15000) {
                inst.uiAutomation.rootInActiveWindow?.packageName?.toString() in candidates
            }
        } finally {
            inst.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        }
    }

    @Test fun unavailableAppsAndDeniedAccessibilityHaveVisibleReplies() {
        send("open nonexistent aitronics test application")
        waitForReply("No installed app matched")
        send("close camera")
        waitForReply("Enable AITRONICS accessibility")
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

    @Test fun downloadedModelAnswersOriginalQuestion() {
        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("modelSmoke") == "true")
        val app=inst.targetContext
        try {
            send("What is the capital of France? Answer with the city name.")
            ui.onNodeWithText("Download & answer · 547 MB").performClick()
            ui.waitUntil(480000) { !LocalModel.downloading }
            org.junit.Assert.assertTrue(LocalModel.downloadMessage,LocalModel.installed(app))
            ui.waitUntil(180000) { ui.onAllNodesWithText("Paris",substring=true,ignoreCase=true).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            val done=java.util.concurrent.CountDownLatch(1)
            LocalModel.delete(app) { done.countDown() }
            done.await(30,java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    @Test fun questionsReplyOrOfferModelOnFreshInstall() {
        send("What is 12 times 9?")
        waitForReply("108")
        send("Explain how a camera sensor works")
        waitForReply("one-time AI download")
        ui.onNodeWithText("Download & answer · 547 MB").assertIsDisplayed()
        send("who are you?")
        waitForReply("I’m AITRONICS")
        ui.onNodeWithText("Download & answer · 547 MB").assertDoesNotExist()
    }

    @Test fun privateCommandsStayOutOfSavedChat() {
        val prefs = ui.activity.getPreferences(android.content.Context.MODE_PRIVATE)
        val previous = prefs.all["saveHistory"] as? Boolean
        val originalHistory = prefs.getString("history", null)
        try {
            prefs.edit().putBoolean("saveHistory", true).commit()
            ui.activityRule.scenario.recreate()
            send("write message private draft specimen")
            waitForReply("Device command")
            val stored = prefs.getString("history", "").orEmpty()
            org.junit.Assert.assertTrue(stored.contains("Device command"))
            org.junit.Assert.assertFalse(stored.contains("private draft specimen"))
        } finally {
            val edit = prefs.edit().putString("history", originalHistory)
            if (previous == null) edit.remove("saveHistory") else edit.putBoolean("saveHistory", previous)
            edit.commit()
        }
    }

    @Test fun sensitivePreviewsAndAuthenticationLabelsAreExcluded() {
        for (value in listOf("Your OTP", "Code 482913", "Enter your passcode", "one-time password", "PIN")) {
            org.junit.Assert.assertTrue(value, PrivateContent.omitPreview(value))
        }
        org.junit.Assert.assertFalse(PrivateContent.omitPreview("Production shift starts at nine"))
    }

    @Test fun deviceChecksValidateTheWholePlan() {
        send("check my battery and then free storage")
        waitForReply("Free storage:")
        val app = ui.activity.applicationContext
        org.junit.Assert.assertTrue(DeviceReadings.answer(app, "battery then send message")!!.contains("No steps ran"))
        org.junit.Assert.assertTrue(DeviceReadings.answer(app, "battery then battery level")!!.contains("repeats"))
        org.junit.Assert.assertTrue(DeviceReadings.answer(app, "date then time then battery then storage then media volume then date")!!.contains("five"))
        org.junit.Assert.assertNull(DeviceReadings.answer(app, "write message bread and milk"))
    }

    @Test fun deviceCommandsWithoutCloudKey() {
        send("battery level")
        waitForReply("Battery level:")
        send("remind me tomorrow")
        waitForReply("water the plants")
    }
}
