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
    private var busy by mutableStateOf(false)
    private var speak by mutableStateOf(true)
    private val messages = mutableStateListOf<Pair<String,String>>()
    private var tts: TextToSpeech? = null
    private var speechReady = false
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) input = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
    }
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
    private fun answer(value: String) {
        messages.add("assistant" to value)
        if (speak && speechReady) tts?.speak(value,TextToSpeech.QUEUE_FLUSH,null,"answer")
    }
    private fun launch(intent: Intent): Boolean = runCatching { startActivity(intent); true }.getOrDefault(false)
    private fun send() {
        val q = input.trim()
        if (q.isEmpty() || busy) return
        input = ""
        messages.add("user" to q)
        val lower = q.lowercase()
        val intent = when {
            lower == "open settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
            lower == "open camera" -> Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            lower == "open youtube" -> Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com"))
            lower.startsWith("dial ") -> Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",q.substring(5),null))
            lower.startsWith("search ") -> Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(q.substring(7))))
            else -> null
        }
        if (intent != null) { answer(if(launch(intent)) "Opened requested action." else "No compatible application found."); return }
        if (apiKey.isBlank()) { answer("Add your OpenAI API key in Settings to enable AI conversations. Offline commands: open settings, open camera, open YouTube, dial a number, or search a topic."); return }
        val secret = apiKey
        val history = messages.takeLast(20).toList()
        busy = true
        Thread {
            var connection: HttpsURLConnection? = null
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
                if(status in 200..299) JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                else "AI request failed (HTTP "+status+"). Check your API key, billing or rate limits."
            } catch(e: Exception) { "Connection failed. Check internet access and try again." }
            finally { connection?.disconnect() }
            runOnUiThread { busy = false; answer(result) }
        }.start()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiKey = loadKey()
        tts = TextToSpeech(this) { speechReady = it == TextToSpeech.SUCCESS }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF64E8FF),background=Color(0xFF08111F),surface=Color(0xFF101E30),onSurface=Color(0xFFE5F5FF))) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().imePadding().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column { Text("AITRONICS",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary); Text(if(apiKey.isBlank()) "OFFLINE COMMANDS" else "AI CONFIGURED",style=MaterialTheme.typography.labelSmall) }
                            TextButton(onClick={settings=true}) { Text("Settings") }
                        }
                        HorizontalDivider()
                        if(messages.isEmpty()) {
                            Text("How can I help?",style=MaterialTheme.typography.headlineSmall)
                            Text("Ask a question or control supported phone functions.")
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={input="open camera";send()}) { Text("Camera") }
                                OutlinedButton(onClick={input="open settings";send()}) { Text("Phone settings") }
                            }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(),reverseLayout=true,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            items(messages.reversed()) { item ->
                                Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=if(item.first=="user") Color(0xFF17405A) else Color(0xFF14263A))) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) { Text(if(item.first=="user") "YOU" else "AITRONICS",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall); Text(item.second) }
                                }
                            }
                        }
                        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedTextField(input,{input=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Ask or enter a command")},maxLines=4,shape=RoundedCornerShape(20.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick={runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }.onFailure { answer("Speech recognition unavailable.") }}) { Text("Voice") }
                            Button(onClick={send()},enabled=!busy,modifier=Modifier.weight(1f)) { Text("Send") }
                        }
                    }
                }
                if(settings) {
                    var draft by remember { mutableStateOf(apiKey) }
                    var error by remember { mutableStateOf(false) }
                    AlertDialog(onDismissRequest={settings=false},title={Text("Assistant settings")},text={
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("Your API key is stored encrypted on this device. API usage is billed separately.")
                            OutlinedTextField(draft,{draft=it},label={Text("OpenAI API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                            Row { Switch(checked=speak,onCheckedChange={speak=it}); Text(" Spoken replies",Modifier.padding(top=12.dp)) }
                            TextButton(onClick={messages.clear()}) { Text("Clear conversation") }
                            if(error) Text("Unable to save key securely.")
                        }
                    },confirmButton={Button(onClick={runCatching { saveKey(draft.trim());apiKey=draft.trim();settings=false }.onFailure { error=true }}) { Text("Save") }},dismissButton={TextButton(onClick={settings=false}) { Text("Cancel") }})
                }
            }
        }
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
