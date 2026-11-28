package com.aitronics.assistant

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ModelTransferTest {
    @Test fun interruptedTransferResumesAndChecksTheWholeFile() {
        val file = File.createTempFile("transfer", ".partial", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        val bytes = "verified test model".toByteArray()
        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun response(status: Int, range: String?, body: ByteArray) = object : HttpURLConnection(URL("https://example.invalid")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = status
            override fun getHeaderField(name: String) = range
            override fun getInputStream() = body.inputStream()
        }
        fun run(connect: (Long) -> HttpURLConnection) = ModelTransfer.download(file, bytes.size.toLong(), checksum, connect, { false }, {})
        try {
            val prefix = bytes.copyOf(5)
            assertTrue(runCatching { run { response(200, null, prefix) } }.isFailure)
            assertArrayEquals(prefix, file.readBytes())
            assertTrue(runCatching { run { response(206, "bytes 0-2/3", bytes) } }.isFailure)
            assertArrayEquals(prefix, file.readBytes())
            run { offset ->
                assertEquals(5L, offset)
                response(206, "bytes 5-${bytes.lastIndex}/${bytes.size}", bytes.copyOfRange(5, bytes.size))
            }
            assertArrayEquals(bytes, file.readBytes())
            file.writeBytes(prefix)
            run { response(200, null, bytes) }
            assertArrayEquals(bytes, file.readBytes())
            file.writeBytes(ByteArray(bytes.size))
            assertTrue(runCatching { run { error("complete file must be checked locally") } }.isFailure)
            assertFalse(file.exists())
        } finally { file.delete() }
    }
}
