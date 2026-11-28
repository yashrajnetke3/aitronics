package com.aitronics.assistant

import android.app.AlertDialog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal class BomPicker(private val activity: ComponentActivity, private val report: (String) -> Unit) {
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val data = ByteArrayOutputStream()
                    requireNotNull(activity.contentResolver.openInputStream(uri)).use { input ->
                        val block = ByteArray(8192)
                        while (true) {
                            val size = input.read(block)
                            if (size < 0) break
                            require(data.size() + size <= 1024 * 1024) { "Use a CSV smaller than 1 MiB." }
                            data.write(block, 0, size)
                        }
                    }
                    BomAnalysis.analyze(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data.toByteArray())).toString())
                }
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            val summary = result.getOrElse {
                report(if (it is IllegalArgumentException && it.message != null) it.message!! else "Could not read this CSV. Choose a UTF-8 CSV with Part Number and Quantity columns.")
                return@launch
            }
            val dialog = AlertDialog.Builder(activity).setTitle("BOM quantities").setMessage(summary)
                .setPositiveButton("Close", null).create()
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.show()
        }
    }

    fun open() {
        runCatching { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream", "application/vnd.ms-excel")) }
            .onFailure { report("No document picker is available.") }
    }
}
