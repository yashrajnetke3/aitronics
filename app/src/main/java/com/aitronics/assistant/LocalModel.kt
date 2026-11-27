package com.aitronics.assistant

import android.content.Context
import android.net.Uri
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

    fun importModel(context: Context, uri: Uri, reply: (String) -> Unit) {
        perform(context, "Import failed. Check available storage and choose a compatible Android MediaPipe LLM .task file.", reply) { app ->
            val root = directory(app)
            val previous = selected(root)?.parentFile
            removeUnused(root, previous)
            val name = UUID.randomUUID().toString()
            val staging = File(root, name)
            if (!staging.mkdirs()) throw IOException("Could not prepare model directory")
            try {
                val candidate = File(staging, "$name.task")
                val stream = app.contentResolver.openInputStream(uri) ?: throw IOException("Could not open document")
                stream.use { input ->
                    candidate.outputStream().use { output ->
                        if (input.copyTo(output, 256 * 1024) == 0L) throw IOException("Empty model")
                        output.fd.sync()
                    }
                }
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
            if (previous != null && !previous.deleteRecursively()) {
                "On-device model ready. Some files from the previous model could not be removed."
            } else "On-device model ready."
        }
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
