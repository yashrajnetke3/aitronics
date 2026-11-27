package com.aitronics.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.*

/** A user-started, visible session scoped to the foreground activity. */
class LocalVoice(private val context: Context, private val end: (String?, String?) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var session: SpeechRecognizer? = null

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        val current = session
        session = null
        current?.destroy()
    }

    fun start(phrase: String?) {
        stop()
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            end(null, "On-device voice recognition is unavailable. Type a command or use Voice in Online mode.")
            return
        }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        session = recognizer
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        fun finish(command: String?, status: String?) {
            if (session !== recognizer) return
            stop()
            end(command, status)
        }
        fun listen() {
            if (session === recognizer) runCatching { recognizer.startListening(request) }
                .onFailure { finish(null, "Could not start the microphone.") }
        }
        fun retry() { handler.postDelayed({ listen() }, 450) }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (session !== recognizer) return
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (phrase == null) finish(heard, null)
                else {
                    val match = Regex("^" + Regex.escape(phrase) + "[, .]+(.+)$", RegexOption.IGNORE_CASE).matchEntire(heard)
                    if (match == null) retry() else finish(match.groupValues[1], null)
                }
            }
            override fun onError(error: Int) {
                if (session !== recognizer) return
                if (phrase != null && error in listOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) retry()
                else finish(null, "On-device voice input ended (code $error). Check the microphone and installed speech language.")
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        handler.postDelayed({ finish(null, "Voice session ended.") }, if (phrase == null) 30000L else 60000L)
        listen()
    }
}
