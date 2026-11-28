package com.aitronics.assistant

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.Executors

object LocalModel {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun directory(context: Context) = File(context.noBackupFilesDir, "local-llm")

    // All writers run on the same executor. The pointer switches atomically only
    // after the copied model has been opened successfully by the native runtime.
    private fun selected(root: File): File? {
        val name = try {
            File(root, "selected").readText()
        } catch (_: FileNotFoundException) {
            return null
        }
        return File(root, "$name/$name.task").takeIf { it.isFile }
    }

    fun installed(context: Context): Boolean = selected(directory(context)) != null

    private fun open(context: Context, model: File): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(model.absolutePath)
            .setMaxTokens(512)
            .build()
        return LlmInference.createFromOptions(context, options)
    }

    private fun perform(context: Context, failure: String, reply: (String) -> Unit, task: (Context) -> String) {
        val app = context.applicationContext
        worker.execute {
            val message = try {
                task(app)
            } catch (_: LinkageError) {
                "The on-device inference runtime is unavailable on this device."
            } catch (_: OutOfMemoryError) {
                "The device has insufficient memory for this model."
            } catch (_: Exception) {
                failure
            }
            main.post { reply(message) }
        }
    }

    private fun removeUnused(root: File, keep: File?) {
        root.listFiles()?.filter { it.isDirectory && it != keep }?.forEach {
            if (!it.deleteRecursively()) throw IOException("Could not remove model files")
        }
    }

    private const val bytes = 546660344L
    private const val sha = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
    private const val source = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/49bfd323c25c9dae2118906e1f3ffd16e0729b13/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    var downloading by mutableStateOf(false); private set
    var progress by mutableIntStateOf(0); private set
    var downloadMessage by mutableStateOf(""); private set
    var completion by mutableIntStateOf(0); private set
    @Volatile private var cancelled = false
    fun cancelDownload() { cancelled = true }
    fun download(context: Context) {
        if (downloading) return
        cancelled=false; progress=0; downloadMessage=""; downloading=true
        perform(context,"Could not finish the download or open the model. Check free space, internet access and device compatibility.",{ result ->
            downloading=false; downloadMessage=if(cancelled) "Download cancelled." else result; completion++
        }) { app ->
            if (app.noBackupFilesDir.usableSpace < bytes + 128L*1024*1024) return@perform "Free at least 700 MB on your phone, then retry."
            install(app) { file ->
                val connection = URL(source).openConnection() as HttpsURLConnection
                connection.connectTimeout=20000; connection.readTimeout=30000
                try {
                    val digest=MessageDigest.getInstance("SHA-256")
                    connection.inputStream.use { input -> file.outputStream().use { output ->
                        val buffer=ByteArray(256*1024)
                        var total=0L; var previous=-1
                        while(true) {
                            check(!cancelled)
                            val count=input.read(buffer)
                            if(count<0) break
                            total+=count
                            require(total<=bytes)
                            output.write(buffer,0,count); digest.update(buffer,0,count)
                            val pct=(100*total/bytes).toInt()
                            if(pct!=previous) { previous=pct; main.post { progress=pct } }
                        }
                        check(total==bytes)
                        output.fd.sync()
                    } }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == sha)
                    check(!cancelled)
                } finally { connection.disconnect() }
            }
        }
    }
    fun importModel(context: Context, uri: Uri, reply: (String) -> Unit) {
        perform(context, "Import failed. Check available storage and choose a compatible Android MediaPipe LLM .task file.", reply) { app ->
            install(app) { candidate ->
                val stream = app.contentResolver.openInputStream(uri) ?: throw IOException("Could not open document")
                stream.use { input -> candidate.outputStream().use { output ->
                    if (input.copyTo(output,256*1024)==0L) throw IOException("Empty model")
                    output.fd.sync()
                } }
            }
        }
    }
    private fun install(app: Context, write: (File)->Unit): String {
            val root = directory(app)
            val previous = selected(root)?.parentFile
            removeUnused(root,previous)
            val name = UUID.randomUUID().toString()
            val staging = File(root,name)
            if (!staging.mkdirs()) throw IOException("Could not prepare model directory")
            try {
                val candidate = File(staging,"$name.task")
                write(candidate)
                open(app, candidate).close()
                val pointer = File(root, "selected.tmp")
                pointer.outputStream().use { out ->
                    out.write(name.toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                Files.move(pointer.toPath(), File(root, "selected").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: Throwable) {
                staging.deleteRecursively()
                throw error
            }
            return if (previous != null && !previous.deleteRecursively()) {
                "On-device model ready. Some files from the previous model could not be removed."
            } else "On-device model ready."
    }

    fun generate(context: Context, prompt: String, reply: (String) -> Unit) {
        perform(context, "On-device inference failed. Try a shorter prompt or another compatible model.", reply) { app ->
            val model = selected(directory(app)) ?: return@perform "Import an on-device model first."
            val engine = open(app, model)
            try {
                engine.generateResponse(prompt).trim().ifEmpty { "The on-device model returned an empty reply." }
            } finally {
                engine.close()
            }
        }
    }

    fun delete(context: Context, reply: (String) -> Unit) {
        perform(context, "Could not remove all model files. Try deleting again.", reply) { app ->
            val root = directory(app)
            Files.deleteIfExists(File(root, "selected").toPath())
            if (root.exists() && !root.deleteRecursively()) throw IOException("Could not remove model files")
            "On-device model removed."
        }
    }
}
