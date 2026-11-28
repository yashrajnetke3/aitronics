package com.aitronics.assistant

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class BackgroundWake : Service() {
    companion object {
        var running by mutableStateOf(false)
            private set
        var status by mutableStateOf("")
            private set
        private var listener: BackgroundWake? = null
        fun listenForQuestion(): Boolean {
            val active = listener ?: return false
            active.triggeredUntil = SystemClock.elapsedRealtime() + 15000
            NeonBubble.wake(15000)
            active.update("Listening for your question…")
            active.handler.removeCallbacks(active.questionTimeout)
            active.handler.postDelayed(active.questionTimeout,15050)
            return true
        }
        internal fun finalTranscript(words: String) { listener?.heard(words) }
        var foregroundCommand: ((String) -> Boolean)? = null
        fun prefs(context: Context) = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        fun enabled(context: Context) = prefs(context).getBoolean("automaticWake",false)
        fun enable(context: Context, value: Boolean) {
            prefs(context).edit().putBoolean("automaticWake",value).apply()
        }
        fun resumeIfEnabled(context: Context) {
            if (running || !enabled(context)) return
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                !(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()) {
                status = "Microphone and notifications are required. Enable them in Android settings."
                return
            }
            runCatching { context.startForegroundService(Intent(context,BackgroundWake::class.java)) }
                .onFailure { status = "Open AITRONICS to resume hands-free listening." }
        }
        private var pending: Pair<String,String>? = null
        private var expires = 0L
        fun take(ticket: String?): String? {
            val item = pending ?: return null
            if (ticket != item.first || SystemClock.elapsedRealtime() > expires) return null
            pending = null
            return item.second
        }
        private val audioWorker = Executors.newSingleThreadExecutor()
        private const val channel = "background_wake"
        private const val id = 721
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var live = false
    private var followUntil = 0L
    private var triggeredUntil = 0L
    private val questionTimeout = Runnable {
        if (triggeredUntil != 0L && SystemClock.elapsedRealtime() >= triggeredUntil) {
            triggeredUntil=0L
            update("No words recognized. Tap Voice input to try again.")
        }
    }
    private var phrase = "hey aitronics"
    private var speaker: TextToSpeech? = null
    @Volatile private var suppressAudioUntil = 0L
    private var speechReady = false
    private val notifications get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    override fun onBind(intent: Intent?) = null
    private fun notice(message: String, ticket: String? = null): Notification {
        val open = Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (ticket != null) open.putExtra("wakeTicket", ticket)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(this,1,Intent(this,BackgroundWake::class.java).setAction("STOP"),flags)
        return Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AITRONICS · Background wake").setContentText(message)
            .setContentIntent(PendingIntent.getActivity(this,0,open,flags)).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") enable(this,false)
        if (!enabled(this)) { status = ""; stopSelf(); return START_NOT_STICKY }
        if (live) return START_STICKY
        notifications.createNotificationChannel(NotificationChannel(channel,"Background wake",NotificationManager.IMPORTANCE_LOW))
        if (!notifications.areNotificationsEnabled() || notifications.getNotificationChannel(channel).importance == NotificationManager.IMPORTANCE_NONE) {
            status = "Enable background wake notifications in Android settings."; stopSelf(); return START_NOT_STICKY
        }
        phrase = prefs(this).getString("wakePhrase","hey aitronics")?.trim()?.ifBlank { "hey aitronics" } ?: "hey aitronics"
        try { startForeground(id,notice("Preparing offline voice recognition…")) }
        catch (_: RuntimeException) { status = "Open AITRONICS and enable microphone access to start wake mode."; stopSelf(); return START_NOT_STICKY }
        live = true; running = true; status = "Preparing offline voice recognition…"
        if (prefs(this).getBoolean("speak",true)) speaker = TextToSpeech(this) { speechReady = it == TextToSpeech.SUCCESS }
        audioWorker.execute {
            try { capture() } catch (_: Exception) { fail("Microphone or voice model unavailable. Stop other recordings and try again.") }
            catch (_: LinkageError) { fail("The offline speech engine is unavailable on this device.") }
        }
        return START_STICKY
    }
    private fun fail(reason: String) = handler.post { if (live) { status = reason; stopSelf() } }
    private fun update(message: String, ticket: String? = null) {
        status = message
        notifications.notify(id,notice(message,ticket))
    }
    private fun modelPath(): File {
        val name = "vosk-model-small-en-us-0.15"
        val ready = File(noBackupFilesDir,name)
        if (ready.isDirectory) return ready
        val stage = File(noBackupFilesDir,"wake-model-partial")
        stage.deleteRecursively(); check(stage.mkdirs())
        try {
            ZipInputStream(assets.open("wake-en-015.zip")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    check(live)
                    if (!entry.isDirectory) {
                        require(entry.name.startsWith("$name/"))
                        val dest = File(stage,entry.name.removePrefix("$name/")).canonicalFile
                        require(dest.path.startsWith(stage.canonicalPath + File.separator))
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            check(stage.renameTo(ready))
        } finally { stage.deleteRecursively() }
        return ready
    }
    private fun capture() {
        val path = modelPath()
        if (!live) return
        Model(path.path).use { model -> Recognizer(model,16000f).use { decoder ->
            if (!live) return
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            val minimum = AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            require(minimum > 0)
            val mic = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,8192))
            try {
                check(mic.state == AudioRecord.STATE_INITIALIZED)
                mic.startRecording()
                check(mic.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                handler.post { if (live) { listener=this; update("Listening for “$phrase”"); NeonBubble.attach(this) } }
                val samples = ShortArray(1600)
                var discarding = false
                while(live) {
                    val count = mic.read(samples,0,samples.size)
                    check(count > 0)
                    if (speaker?.isSpeaking == true || SystemClock.elapsedRealtime() < suppressAudioUntil) {
                        if (!discarding) decoder.reset()
                        discarding = true
                        continue
                    }
                    if (discarding) { decoder.reset(); discarding = false }
                    NeonBubble.meter(samples,count)
                    if (decoder.acceptWaveForm(samples,count)) {
                        val words = JSONObject(decoder.result).optString("text")
                        if (words.isNotBlank()) handler.post { if (live) finalTranscript(words) }
                    }
                }
            } finally { runCatching { mic.stop() }; mic.release() }
        } }
    }
    private fun heard(words: String) {
        if (speaker?.isSpeaking == true) return
        val now = SystemClock.elapsedRealtime()
        val tail = WakeCommands.afterWake(words,phrase)
        val command = when {
            tail != null -> tail
            now <= triggeredUntil -> words
            now <= followUntil && ScreenCommands.followUp(WakeCommands.normalize(words,phrase)) -> words
            else -> return
        }
        triggeredUntil = if (command.isBlank()) now + 10000 else 0
        val locked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        if (locked) { triggeredUntil = 0; followUntil = 0; update("Unlock your phone to give a command."); return }
        if(tail != null) NeonBubble.wake()
        if(command.isBlank() && NeonBubble.overOtherApps && NeonBubble.awake) {
            update("Listening for your command…")
            handler.removeCallbacks(questionTimeout)
            handler.postDelayed(questionTimeout,10050)
            return
        }
        if(command.isNotBlank()) NeonBubble.received()
        val normalized = WakeCommands.normalize(command,phrase)
        followUntil = if (normalized.startsWith("open ",true) || ScreenCommands.handles(normalized)) now + 15000 else 0
        if (normalized.isNotBlank()) {
            val screenAction = ScreenCommands.handles(normalized) && foregroundCommand == null
            val reply = runCatching {
                if (screenAction) AssistantAccessibility.active?.let { ScreenCommands.run(it,normalized) } ?: "Enable AITRONICS in Accessibility settings first."
                else WakeCommands.local(this,normalized)
            }.getOrElse { "Device action unavailable." }
            if (reply != null) {
                pending = null; update(if (normalized.lowercase() in setOf("read draft","send message")) "Review your message in the open chat." else reply)
                if (speechReady) {
                    suppressAudioUntil = SystemClock.elapsedRealtime() + 600
                    speaker?.speak(reply,TextToSpeech.QUEUE_FLUSH,null,"wake-reply")
                }
                return
            }
        }
        if (normalized.isNotBlank()) update("Listening for “$phrase”")
        if (foregroundCommand?.invoke(normalized) == true) return
        val ticket = UUID.randomUUID().toString()
        pending = ticket to normalized; expires = now + 60000
        update(if (normalized.isEmpty()) "Wake phrase heard. Tap to open AITRONICS." else "Command ready. Tap to continue in AITRONICS.",ticket)
        SystemAssistant.active?.let { assistant -> runCatching {
            assistant.showSession(Bundle().apply { putString("wakeTicket",ticket) },0)
        } }
    }
    override fun onDestroy() {
        if(listener === this) listener=null
        NeonBubble.detach()
        live = false; running = false; pending = null
        speaker?.shutdown(); handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
