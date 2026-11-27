package com.aitronics.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.*

class AssistantRecognition : RecognitionService() {
    private var delegate: SpeechRecognizer? = null
    override fun onStartListening(intent: Intent, listener: Callback) {
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); return
        }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        delegate = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listener.readyForSpeech(params ?: Bundle()) }
            override fun onBeginningOfSpeech() { listener.beginningOfSpeech() }
            override fun onRmsChanged(rms: Float) { listener.rmsChanged(rms) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listener.endOfSpeech() }
            override fun onPartialResults(results: Bundle?) { if(results != null) listener.partialResults(results) }
            override fun onResults(results: Bundle?) { listener.results(results ?: Bundle()); close() }
            override fun onError(error: Int) { listener.error(error); close() }
            override fun onEvent(type: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }
    private fun close() { delegate?.destroy(); delegate = null }
    override fun onStopListening(listener: Callback) { delegate?.stopListening() }
    override fun onCancel(listener: Callback) { close() }
    override fun onDestroy() { close(); super.onDestroy() }
}
