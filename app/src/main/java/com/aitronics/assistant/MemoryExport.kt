package com.aitronics.assistant

import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStream
import java.security.MessageDigest

internal class MemoryExport(private val activity: ComponentActivity, private val report: (String) -> Unit) {
    private var ticket: Bundle? = null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val reviewed = ticket
        ticket = null
        if (uri == null || reviewed == null) {
            report("Export cancelled.")
        } else {
            MemoryDatabase.executor.execute {
                val message = runCatching {
                    val current = MemoryDatabase.get(activity).notes().snapshot(reviewed.getLong("id"))
                    writeReviewed(current, reviewed.getByteArray("digest")) {
                        activity.contentResolver.openOutputStream(uri, "wt")
                    }
                }.getOrDefault("Export failed. The destination may contain an incomplete file.")
                activity.runOnUiThread { if (!activity.isDestroyed) report(message) }
            }
        }
    }

    fun save(state: Bundle) = state.putBundle("memoryExport", ticket)
    fun restore(state: Bundle?) { ticket = state?.getBundle("memoryExport") }

    fun start(command: String) {
        val id = command.substringAfterLast(' ').removePrefix("#").toLongOrNull()
        if (id == null || id < 1) { report("Use recall memory, then say export memory followed by its number."); return }
        if (ticket != null) { report("Finish the current export first."); return }
        MemoryDatabase.executor.execute {
            val result = runCatching { MemoryDatabase.get(activity).notes().snapshot(id)?.let { it to NoteCipher.decrypt(it.note) } }
            activity.runOnUiThread {
                if (activity.isDestroyed || activity.isFinishing) return@runOnUiThread
                val note = result.getOrElse { report("Could not read personal memory."); return@runOnUiThread }
                if (note == null) { report("No memory with that number."); return@runOnUiThread }
                val dialog = AlertDialog.Builder(activity).setTitle("Export memory #$id as plain text?")
                    .setMessage("Anyone with access to the destination can read this copy.\n\n" + note.second)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Choose destination") { _, _ ->
                        if (ticket != null) return@setPositiveButton
                        ticket = Bundle().apply { putLong("id", id); putByteArray("digest", digest(note.first)) }
                        runCatching { picker.launch("AITRONICS-note-$id.txt") }.onFailure {
                            ticket = null
                            report("No document picker is available.")
                        }
                    }.create()
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.show()
            }
        }
    }

    companion object {
        fun digest(note: Memory): ByteArray = MessageDigest.getInstance("SHA-256").digest(note.note.toByteArray())
        fun writeReviewed(current: Memory?, expected: ByteArray?, open: () -> OutputStream?): String {
            if (current == null || expected == null || !MessageDigest.isEqual(digest(current), expected))
                return "The memory changed. Review it again before exporting."
            val bytes = NoteCipher.decrypt(current.note).toByteArray(Charsets.UTF_8)
            val output = open() ?: throw java.io.IOException("Destination unavailable")
            output.use { it.write(bytes) }
            return "Memory exported."
        }
    }
}
