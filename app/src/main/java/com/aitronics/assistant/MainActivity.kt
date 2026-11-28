package com.aitronics.assistant

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : ComponentActivity() {
    private var input by mutableStateOf("")
    private var apiKey by mutableStateOf("")
    private var settings by mutableStateOf(false)
    private var pendingQuestion by mutableStateOf("")
    private var seenCompletion = 0
    private var busy by mutableStateOf(false)
    private var mode by mutableStateOf("Auto")
    private var overlayAllowed by mutableStateOf(false)
    private var speak by mutableStateOf(true)
    private val messages = mutableStateListOf<Pair<String,String>>()
    private var tts: TextToSpeech? = null
    private var speechReady = false
    private val memoryExport = MemoryExport(this, ::answer)
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (text.isBlank()) answer("I didn’t hear a question. Tap Voice input and try again.") else { input=WakeCommands.normalizeSpeech(text,wakePhrase); send() }
        }
    }
    private var wakePhrase by mutableStateOf("hey aitronics")
    private var listening by mutableStateOf(false)
    private var wakeRequested = false
    private val cloudHistory = mutableListOf<Pair<String,String>>()
    private var saveHistory by mutableStateOf(false)
    private lateinit var localVoice: LocalVoice
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLocalVoice() else answer("Microphone permission is needed for on-device voice.")
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        answer(if (granted) "Reminder notifications enabled. Enter your reminder command." else "Reminder notifications are disabled.")
    }
    private fun startLocalVoice() {
        listening = true
        runCatching { localVoice.start(if (wakeRequested) wakePhrase else null) }
            .onFailure { localVoice.stop(); listening = false; answer("Could not start on-device voice.") }
    }
    private fun listen(wake: Boolean) {
        if (!wake && BackgroundWake.running) {
            if (!BackgroundWake.listenForQuestion()) answer("The microphone is starting. Try Voice input again in a moment.")
            return
        }
        if (listening) { localVoice.stop(); listening = false; return }
        if (!wake && mode == "Online") {
            runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                .onFailure { answer("Speech recognition unavailable.") }
            return
        }
        wakeRequested = wake
        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    private fun memory(privateReply: Boolean = false, action: (MemoryDao) -> String) {
        busy = true
        MemoryDatabase.executor.execute {
            val reply = runCatching { action(MemoryDatabase.get(applicationContext).notes()) }.getOrDefault("Could not access local memory.")
            runOnUiThread {
                busy = false
                if (!isDestroyed) {
                    if (privateReply) android.app.AlertDialog.Builder(this)
                        .setTitle("Personal memory").setMessage(reply).setPositiveButton("Close", null).show()
                    else answer(reply)
                }
            }
        }
    }
    private val chooseModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            settings = false
            LocalModel.importModel(this, uri) { reply -> busy = false; if (!isDestroyed) answer(reply) }
        }
    }
    private val backgroundPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val mic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val notifications = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (mic && notifications.areNotificationsEnabled()) {
            localVoice.stop(); listening = false
            BackgroundWake.enable(this,true)
            BackgroundWake.resumeIfEnabled(this)
        } else answer("Background wake needs microphone access and a visible notification with a Stop button.")
    }
    private fun backgroundWake() {
        if (BackgroundWake.enabled(this)) { BackgroundWake.enable(this,false); stopService(Intent(this,BackgroundWake::class.java)); return }
        android.app.AlertDialog.Builder(this).setTitle("Set up hands-free listening")
            .setMessage("After this one-time setup, AITRONICS automatically listens for “$wakePhrase” when opened or activated as your default assistant. Audio stays on this phone. Continuous listening uses battery. Stop from the notification to turn automatic listening off.")
            .setNegativeButton("Cancel",null).setPositiveButton("Enable hands-free") { _,_ ->
                val perms = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
                if (android.os.Build.VERSION.SDK_INT >= 33) perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                backgroundPermissions.launch(perms.toTypedArray())
            }.show()
    }
    private fun selectAssistant() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(android.app.role.RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                if (roles.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)) answer("AITRONICS is already your default assistant.")
                else launch(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT))
                return
            }
        }
        launch(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
    }
    private fun fromWake(command: String): Boolean {
        if (busy) return false
        if (command.isEmpty()) messages.add("assistant" to "Listening for your command.")
        else { input = command; send() }
        return true
    }
    private fun consumeWake() {
        if (busy) return
        BackgroundWake.take(intent?.getStringExtra("wakeTicket"))?.let { command ->
            intent.removeExtra("wakeTicket")
            fromWake(command)
        }
    }
    override fun onNewIntent(newIntent: Intent) { super.onNewIntent(newIntent); setIntent(newIntent); consumeWake() }
    override fun onResume() { super.onResume(); overlayAllowed=android.provider.Settings.canDrawOverlays(this); NeonBubble.foreground(true); BackgroundWake.resumeIfEnabled(this); BackgroundWake.foregroundCommand = ::fromWake; consumeWake() }
    override fun onPause() { NeonBubble.foreground(false); BackgroundWake.foregroundCommand = null; super.onPause() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("aitronics_api", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("aitronics_api",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun saveKey(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val enc = cipher.doFinal(value.toByteArray())
        getPreferences(MODE_PRIVATE).edit().putString("secret",Base64.encodeToString(cipher.iv + enc,Base64.NO_WRAP)).apply()
    }
    private fun loadKey(): String = runCatching {
        val data = Base64.decode(getPreferences(MODE_PRIVATE).getString("secret",""),Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12)))
        String(cipher.doFinal(data.copyOfRange(12,data.size)))
    }.getOrDefault("")
    private fun persistHistory() {
        if (!saveHistory) return
        val array = JSONArray()
        messages.takeLast(100).forEach { array.put(JSONObject().put("role",it.first).put("text",it.second)) }
        getPreferences(MODE_PRIVATE).edit().putString("history",array.toString()).apply()
    }
    private fun answer(value: String) {
        messages.add("assistant" to value)
        persistHistory()
        if (speak && speechReady) tts?.speak(value.substringBefore("\n\nhttps://en.wikipedia.org/"),TextToSpeech.QUEUE_FLUSH,null,"answer")
    }
    private fun launch(intent: Intent): Boolean = runCatching { startActivity(intent); true }.getOrDefault(false)
    private fun lookUp(topic: String) {
        busy=true
        Thread {
            val reply=runCatching { TopicLookup.fetch(topic) }.getOrDefault("Wikipedia is unavailable. Check your connection and try again.")
            runOnUiThread { busy=false; if(!isDestroyed) answer(reply) }
        }.start()
    }
    private fun askLocal(q: String) {
        busy=true
        LocalModel.generate(this,q) { reply ->
            busy=false
            if(!isDestroyed) { answer(reply); consumeWake() }
        }
    }
    private fun deviceCommand(q: String): String? {
        val s = q.lowercase().trim().removePrefix("hey aitronics").trim().trimStart(',').trim()
        if(s == "read screen" || ScreenCommands.handles(q)) {
            val service = AssistantAccessibility.active ?: return "Enable AITRONICS accessibility first."
            android.app.AlertDialog.Builder(this).setMessage("Switch back to the previous app and run “$s” on that screen?")
                .setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_ ->
                    if (!moveTaskToBack(true)) {
                        answer("Android could not switch apps.")
                    } else android.os.Handler(mainLooper).postDelayed({
                        if (!isDestroyed) {
                            val locked = (getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceLocked
                            val reply = when {
                                locked -> "Unlock your phone to use screen commands."
                                AssistantAccessibility.active !== service -> "Accessibility disconnected."
                                s == "read screen" -> service.screenText()
                                else -> runCatching { ScreenCommands.run(service,q) }.getOrDefault("This app could not complete the screen command.")
                            }
                            if (speak && speechReady) tts?.speak(reply,TextToSpeech.QUEUE_FLUSH,null,"screen-action")
                            else android.widget.Toast.makeText(this,reply,android.widget.Toast.LENGTH_LONG).show()
                        }
                    }, 900L)
                }.show()
            return "Review the screen action."
        }
        val navigation = when(s) {
            "go back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            "go home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            "recent apps" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            "show notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick settings" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            else -> null
        }
        if(navigation != null) {
            val service = AssistantAccessibility.active ?: return "Enable AITRONICS navigation in Android accessibility settings first."
            return if(service.performGlobalAction(navigation)) "Navigation action accepted." else "Android rejected the navigation action."
        }
        WakeCommands.local(this,s)?.let { return it }
        val alarm = Regex("set alarm (?:for )?(\\d{1,2}):(\\d{2})").matchEntire(s)
        if(alarm != null) {
            val h=alarm.groupValues[1].toInt(); val m=alarm.groupValues[2].toInt()
            if(h !in 0..23 || m !in 0..59) return "Use a valid 24-hour time."
            return if(launch(Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR,h).putExtra(android.provider.AlarmClock.EXTRA_MINUTES,m))) "Opened alarm setup." else "Alarm application unavailable."
        }
        val action = when(s) {
            "open wifi settings" -> android.provider.Settings.ACTION_WIFI_SETTINGS
            "open bluetooth settings" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            "open accessibility settings" -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            "open battery settings" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> null
        }
        if(action != null) return if(launch(Intent(action))) "Opened requested settings." else "Settings unavailable."
        return null
    }
    private fun send() {
        val q = WakeCommands.normalize(input,wakePhrase)
        if (busy || input.isBlank()) return
        if (q.isBlank()) { input=""; answer("I’m listening. What would you like to ask?"); return }
        pendingQuestion=""
        val keyboard = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        keyboard.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        input = ""
        val privateCommand = ScreenCommands.handles(q) || q.equals("read screen", true) || q.startsWith("message ",true)
        messages.add("user" to when { privateCommand -> "Device command"; q.startsWith("remember ",true) -> "Save personal memory"; MemoryReview.handles(q) -> "Manage personal memory"; else -> q })
        runCatching { deviceCommand(q) }.getOrElse { "Device action unavailable or permission denied." }?.let { answer(it); return }
        val lower = q.lowercase()
        if (lower == "help") {
            answer("Try Hey, open WhatsApp; open chat with Sam; write message I am on my way; read draft; send message; confirm send; scroll down; close camera; battery level, volume up, read screen, scroll down, dial 12345, message 12345, summarize notifications, remember my shift starts at nine, recall memory, export memory 3, or remind me in 10 minutes to check the line.")
            return
        }
        if (lower.startsWith("remember ")) { memory { it.add(Memory(note=q.substringAfter(' ').take(4000))); "Saved to local memory." }; return }
        if (lower == "recall memory") {
            memory(privateReply = true) { dao -> dao.records().joinToString("\n") { "#${it.id}  ${NoteCipher.decrypt(it.note)}" }
                .ifBlank { "No saved memories." } }
            return
        }
        if (lower == "export memory" || lower.startsWith("export memory ")) { memoryExport.start(lower); return }
        if (MemoryReview.handles(q)) { MemoryReview.open(this, q, ::answer); return }
        if (lower == "forget all memories") {
            android.app.AlertDialog.Builder(this).setMessage("Delete all saved memories?").setNegativeButton("Cancel",null)
                .setPositiveButton("Delete") { _, _ -> memory { it.clear(); "Memories deleted." } }.show()
            return
        }
        if (lower == "read notifications") {
            android.app.AlertDialog.Builder(this).setMessage("Show up to eight recent notification previews on this phone?")
                .setNegativeButton("Cancel", null).setPositiveButton("Show") { _,_ ->
                    android.app.AlertDialog.Builder(this).setTitle("Notification previews")
                        .setMessage(AssistantNotificationListener.previews(this)).setPositiveButton("Close",null).show()
                }.show()
            return
        }
        if (lower == "summarize notifications") { answer(AssistantNotificationListener.summary(this)); return }
        if (Reminders.handle(this, q) { reply -> if (!isDestroyed) answer(reply) }) return
        val intent = when {
            lower == "open settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
            lower == "open camera" -> Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            lower == "open contacts" -> Intent(Intent.ACTION_VIEW,android.provider.ContactsContract.Contacts.CONTENT_URI)
            lower == "open youtube" -> Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com"))
            lower.startsWith("dial ") -> Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",q.substring(5),null))
            lower.startsWith("message ") -> {
                val message = q.substring(8).trim()
                val split = Regex("""\s+saying\s+""", RegexOption.IGNORE_CASE).split(message, limit=2)
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto",split.first(),null)).apply {
                    if (split.size == 2) putExtra("sms_body",split[1])
                }
            }
            lower.startsWith("search ") -> Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(q.substring(7))))
            else -> null
        }
        if (intent != null) { answer(if(launch(intent)) "Opened requested action." else "No compatible application found."); return }
        if (lower.startsWith("open ")) { answer(runCatching { openInstalledApp(this, q.substringAfter(' ')) }.getOrDefault("App could not be opened.")); return }
        QuickAnswers.answer(q)?.let { answer(it); return }
        if (ChatRouting.useLocal(mode, apiKey.isNotBlank()) { ChatRouting.connected(this) }) {
            if (LocalModel.installed(this)) askLocal(q)
            else {
                pendingQuestion=q
                answer("For general chat, tap Download & answer for a one-time AI download, or add an OpenAI API key in Settings.")
            }
            return
        }
        val secret = apiKey
        val history = cloudHistory.takeLast(18) + listOf("user" to q)
        busy = true
        Thread {
            var connection: HttpsURLConnection? = null
            var completed = false
            var unavailable = false
            val result = try {
                connection = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty("Authorization","Bearer "+secret)
                connection.setRequestProperty("Content-Type","application/json")
                val array = JSONArray().put(JSONObject().put("role","system").put("content","You are AITRONICS, a concise personal assistant. Never claim to execute phone actions you cannot perform."))
                history.forEach { array.put(JSONObject().put("role",it.first).put("content",it.second)) }
                val body = JSONObject().put("model","gpt-4o-mini").put("messages",array)
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val status = connection.responseCode
                if(status in 200..299) JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").also { completed = it.isNotBlank() }
                else {
                    unavailable = status == 408 || status in 500..599
                    "AI request failed (HTTP "+status+"). Check your API key, billing or rate limits."
                }
            } catch(e: java.io.IOException) {
                unavailable = true
                "Connection failed. Check internet access and try again."
            } catch(e: Exception) { "The cloud service returned an unreadable reply." }
            finally { connection?.disconnect() }
            runOnUiThread {
                if (!isDestroyed) {
                    busy = false
                    if (unavailable && mode == "Auto" && LocalModel.installed(this)) {
                        answer("Cloud unavailable. Answering on this phone.")
                        askLocal(q)
                        return@runOnUiThread
                    }
                    if (completed) {
                        cloudHistory.clear()
                        cloudHistory.addAll(history + listOf("assistant" to result))
                    }
                    answer(result.ifBlank { "The AI returned an empty reply. Please retry your question." })
                }
            }
        }.start()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryExport.restore(savedInstanceState)
        pendingQuestion=savedInstanceState?.getString("pendingQuestion").orEmpty()
        seenCompletion=savedInstanceState?.getInt("seenCompletion") ?: LocalModel.completion
        NeonBubble.load(this)
        val prefs = getPreferences(MODE_PRIVATE)
        speak = prefs.getBoolean("speak",true)
        saveHistory = prefs.getBoolean("saveHistory", prefs.contains("history"))
        wakePhrase = prefs.getString("wakePhrase","hey aitronics") ?: "hey aitronics"
        localVoice = LocalVoice(this) { command, status ->
            listening = false
            if (!command.isNullOrBlank()) { input = WakeCommands.normalizeSpeech(command,wakePhrase); send() }
            else answer(status ?: "I didn’t hear any words. Try Voice input again or type your question.")
        }
        apiKey = loadKey()
        runCatching { val a = JSONArray(getPreferences(MODE_PRIVATE).getString("history","[]")); for(i in 0 until a.length()) { val m=a.getJSONObject(i); messages.add(m.getString("role") to m.getString("text")) } }
        mode = getPreferences(MODE_PRIVATE).getString("mode","Auto") ?: "Auto"
        tts = TextToSpeech(this) { speechReady = it == TextToSpeech.SUCCESS }
        setContent {
            LaunchedEffect(LocalModel.completion) {
                if(seenCompletion!=LocalModel.completion) {
                    seenCompletion=LocalModel.completion
                    if(pendingQuestion.isNotBlank() && LocalModel.installed(this@MainActivity)) {
                        val question=pendingQuestion; pendingQuestion=""; askLocal(question)
                    } else if(LocalModel.downloadMessage.isNotBlank()) answer(LocalModel.downloadMessage)
                }
            }
            MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Night,background=Night,surface=Panel,onSurface=Color.White)) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF131D29),Night,Night)))) {
                    Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        AssistantHeader(when { mode=="Offline" -> "Offline assistant"; apiKey.isBlank() && LocalModel.installed(this@MainActivity) -> "Offline AI ready"; apiKey.isBlank() -> "On-device commands"; else -> "$mode · Cloud configured" }) { settings=true }
                        HorizontalDivider(color=Edge)
                        if (messages.isEmpty()) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                val orbSize = (maxHeight * .42f).coerceIn(72.dp,170.dp)
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                    verticalArrangement=Arrangement.Center) {
                                    HomePanel(BackgroundWake.running,wakePhrase,orbSize,{ input="open camera";send() },{ input="battery level";send() })
                                }
                            }
                        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),reverseLayout=true,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            items(messages.reversed()) { item -> ChatMessage(item.first=="user",item.second) }
                        }
                        if(listening) Text(if(wakeRequested) "Listening for “$wakePhrase” followed by a command" else "Listening…")
                        if(BackgroundWake.status.isNotEmpty() && BackgroundWake.enabled(this@MainActivity)) Text(BackgroundWake.status)
                        if(LocalModel.downloading) {
                            Text(if(LocalModel.progress < 100) "Downloading offline AI · ${LocalModel.progress}%" else "Preparing the answer model…")
                            if(LocalModel.progress < 100) Row {
                                TextButton(onClick={LocalModel.pauseDownload()}) { Text("Pause") }
                                TextButton(onClick={LocalModel.cancelDownload()}) { Text("Cancel download") }
                            }
                        } else if(pendingQuestion.isNotBlank() && !busy) {
                            if(mode!="Offline") TopicLookup.topic(pendingQuestion)?.let { topic ->
                                TextButton(onClick={lookUp(topic)}) { Text("Look up on Wikipedia") }
                            }
                            Button(onClick={LocalModel.download(this@MainActivity)}) { Text("Download & answer · 547 MB") }
                        }
                        if(busy) { Text("Working on your reply…",color=Muted); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        CommandComposer(
                            input=input, change={input=it}, send=::send, voice={listen(false)}, wake=::backgroundWake,
                            listening=listening, busy=busy, wakeRunning=BackgroundWake.running,
                            wakeEnabled=BackgroundWake.enabled(this@MainActivity)
                        )
                    }
                    if(NeonBubble.enabled && NeonBubble.awake) {
                        androidx.compose.ui.viewinterop.AndroidView(factory={ WakePanel(it) },
                            update={ it.showState(NeonBubble.listening) },
                            modifier=Modifier.align(Alignment.BottomCenter).safeDrawingPadding().imePadding()
                                .padding(horizontal=12.dp,vertical=18.dp).widthIn(max=360.dp).fillMaxWidth())
                    }
                }
                if(settings) {
                    var draft by remember { mutableStateOf(apiKey) }
                    var error by remember { mutableStateOf(false) }
                    AlertDialog(onDismissRequest={settings=false},title={Text("Assistant settings")},text={
                        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            BubbleOptions(overlayAllowed,{show,pin -> NeonBubble.configure(this@MainActivity,show,pin)}) {
                                val action=android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                                if(!launch(Intent(action,Uri.parse("package:"+packageName)))) launch(Intent(action))
                            }
                            HorizontalDivider(color=Edge)
                            Text("Assistant mode")
                            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                listOf("Auto","Offline","Online").forEach { option ->
                                    FilterChip(selected=mode==option,onClick={mode=option;getPreferences(MODE_PRIVATE).edit().putString("mode",option).apply()},label={Text(option)})
                                }
                            }
                            Text("Offline chat runs on your phone with Qwen2.5 0.5B. Cloud chat uses your OpenAI API key.")
                            if(!LocalModel.installed(this@MainActivity)) TextButton(enabled=!busy && !LocalModel.downloading,onClick={settings=false;LocalModel.download(this@MainActivity)}) { Text("Download offline AI · 547 MB") }
                            TextButton(enabled=!busy && !LocalModel.downloading,onClick={chooseModel.launch(arrayOf("*/*"))}) { Text("Import offline model") }
                            if (LocalModel.installed(this@MainActivity)) TextButton(enabled=!busy,onClick={
                                android.app.AlertDialog.Builder(this@MainActivity).setMessage("Delete the imported offline model?")
                                    .setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ ->
                                        busy=true; settings=false
                                        LocalModel.delete(this@MainActivity) { reply -> busy=false; if (!isDestroyed) answer(reply) }
                                    }.show()
                            }) { Text("Delete offline model") }
                            OutlinedTextField(draft,{draft=it},label={Text("OpenAI API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                            Row { Switch(checked=speak,onCheckedChange={speak=it;getPreferences(MODE_PRIVATE).edit().putBoolean("speak",it).apply()}); Text(" Spoken replies",Modifier.padding(top=12.dp)) }
                            TextButton(onClick={selectAssistant()}) { Text("Set default assistant for hands-free opening") }
                            OutlinedTextField(wakePhrase,{wakePhrase=it.take(48)},label={Text("Wake phrase")},singleLine=true)
                            Row { Switch(checked=saveHistory,onCheckedChange={saveHistory=it;getPreferences(MODE_PRIVATE).edit().putBoolean("saveHistory",it).remove("history").apply();persistHistory()}); Text(" Save history on device") }
                            TextButton(onClick={launch(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}) { Text("Notification access") }
                            TextButton(onClick={launch(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))}) { Text("Accessibility settings") }
                            TextButton(onClick={if(android.os.Build.VERSION.SDK_INT>=33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else launch(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))}) { Text("Reminder permissions") }
                            TextButton(onClick={messages.clear(); cloudHistory.clear();persistHistory()}) { Text("Clear conversation") }
                            if(error) Text("Unable to save key securely.")
                        }
                    },confirmButton={Button(onClick={runCatching { wakePhrase=wakePhrase.trim().ifBlank { "hey aitronics" };getPreferences(MODE_PRIVATE).edit().putString("wakePhrase",wakePhrase).apply();saveKey(draft.trim());apiKey=draft.trim();settings=false; if(pendingQuestion.isNotBlank() && apiKey.isNotBlank() && mode!="Offline" && !busy) { input=pendingQuestion; send() } }.onFailure { error=true }}) { Text("Save") }},dismissButton={TextButton(onClick={settings=false}) { Text("Cancel") }})
                }
            }
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        memoryExport.save(state)
        state.putString("pendingQuestion",pendingQuestion)
        state.putInt("seenCompletion",seenCompletion)
    }
    override fun onStop() { localVoice.stop(); listening=false; super.onStop() }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

