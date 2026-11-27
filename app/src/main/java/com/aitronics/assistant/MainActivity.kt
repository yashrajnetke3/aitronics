package com.aitronics.assistant

import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var input by mutableStateOf("")
    private var reply by mutableStateOf("Assistant ready. AI service is not configured.")
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) input = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("AITRONICS", style = MaterialTheme.typography.headlineLarge)
                        Text("Personal intelligent assistant")
                        Text(reply)
                        OutlinedTextField(input, { input = it }, label = { Text("Enter a command") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { reply = if (input.isBlank()) "Enter a command first." else "AI service is not configured. Command received: " + input }) { Text("Send") }
                        OutlinedButton(onClick = {
                            try {
                                voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                            } catch (e: Exception) { reply = "Speech recognition unavailable." }
                        }) { Text("Voice input") }
                    }
                }
            }
        }
    }
}