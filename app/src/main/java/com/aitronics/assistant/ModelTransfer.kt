package com.aitronics.assistant

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest

internal object ModelTransfer {
    fun download(file: File, size: Long, checksum: String, connect: (Long) -> HttpURLConnection,
                 stop: () -> Boolean, progress: (Int) -> Unit) {
        if (file.length() > size) file.delete()
        val start = file.length()
        val buffer = ByteArray(256 * 1024)
        if (start < size) {
            val connection = connect(start)
            try {
                val offset = when (connection.responseCode) {
                    200 -> 0L
                    206 -> {
                        if (connection.getHeaderField("Content-Range") != "bytes $start-${size - 1}/$size")
                            throw IOException("Unexpected download range")
                        start
                    }
                    else -> throw IOException("Download returned HTTP " + connection.responseCode)
                }
                if (file.parentFile!!.usableSpace < size - offset + 128L * 1024 * 1024)
                    throw IOException("Insufficient storage")
                var total = offset
                var lastPercent = -1
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(file, offset > 0).use { output ->
                        while (true) {
                            if (stop()) throw InterruptedException()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (total + count > size) throw IOException("Oversized model")
                            output.write(buffer, 0, count)
                            total += count
                            val percent = (100 * total / size).toInt()
                            if (percent != lastPercent) { progress(percent); lastPercent = percent }
                        }
                        output.fd.sync()
                    }
                }
            } finally { connection.disconnect() }
        }
        if (file.length() != size) throw IOException("Incomplete download")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            while (true) {
                if (stop()) throw InterruptedException()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != checksum) {
            file.delete()
            throw IOException("Model checksum mismatch")
        }
    }
}
