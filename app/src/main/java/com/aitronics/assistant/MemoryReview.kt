package com.aitronics.assistant

import android.app.Activity
import android.app.AlertDialog
import android.view.WindowManager
import android.widget.EditText

internal object MemoryReview {
    private val command = Regex("""(edit|forget|delete) memory\s+#?(\d+)""", RegexOption.IGNORE_CASE)
    fun handles(input: String) = command.matches(input)

    fun open(activity: Activity, input: String, report: (String) -> Unit) {
        val match = command.matchEntire(input) ?: return
        val id = match.groupValues[2].toLongOrNull()
        if (id == null) { report("Invalid memory number."); return }
        val editing = match.groupValues[1].equals("edit", true)
        val database = MemoryDatabase.get(activity)
        MemoryDatabase.executor.execute {
            val result = runCatching {
                database.notes().snapshot(id)?.let { it to NoteCipher.decrypt(it.note) }
            }
            activity.runOnUiThread {
                if (activity.isDestroyed || activity.isFinishing) return@runOnUiThread
                val pair = result.getOrElse { report("Could not read personal memory."); return@runOnUiThread }
                if (pair == null) { report("No memory with that number."); return@runOnUiThread }
                val (record, text) = pair
                val editor = if (editing) EditText(activity).apply {
                    setText(text)
                    filters = arrayOf(android.text.InputFilter.LengthFilter(4000))
                } else null
                val builder = AlertDialog.Builder(activity).setTitle(if (editing) "Edit memory #$id" else "Delete memory #$id?")
                    .setNegativeButton("Cancel", null)
                if (editor == null) builder.setMessage(text) else builder.setView(editor)
                val dialog = builder.setPositiveButton(if (editing) "Save" else "Delete") { _, _ ->
                    val replacement = editor?.text?.toString()
                    if (replacement != null && replacement.isBlank()) {
                        report("A memory needs some text.")
                        return@setPositiveButton
                    }
                    MemoryDatabase.executor.execute {
                        val reply = runCatching {
                            val changed = if (replacement == null) database.notes().deleteIfCurrent(id, record.note) == 1
                                else database.notes().correct(record, replacement)
                            when {
                                !changed -> "The memory changed. Review it again."
                                editing -> "Memory updated."
                                else -> "Memory deleted."
                            }
                        }.getOrDefault("Could not update personal memory.")
                        activity.runOnUiThread { if (!activity.isDestroyed && !activity.isFinishing) report(reply) }
                    }
                }.create()
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.show()
            }
        }
    }
}
