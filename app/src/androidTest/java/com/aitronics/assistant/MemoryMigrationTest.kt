package com.aitronics.assistant

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MemoryMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exportWritesOnlyReviewedContentAndReportsFailedWrites() {
        val note = Memory(7, NoteCipher.encrypt("शिफ्ट २"))
        val reviewed = MemoryExport.digest(note)
        val output = java.io.ByteArrayOutputStream()
        assertEquals("Memory exported.", MemoryExport.writeReviewed(note, reviewed) { output })
        assertEquals("शिफ्ट २", output.toString("UTF-8"))
        for (item in listOf(null, note.copy(note = NoteCipher.encrypt("changed")))) {
            assertTrue(MemoryExport.writeReviewed(item, reviewed) { error("must not open") }.contains("changed"))
        }
        assertTrue(runCatching { MemoryExport.writeReviewed(note, reviewed) { null } }.isFailure)
        assertTrue(runCatching { MemoryExport.writeReviewed(note, reviewed) {
            object : java.io.OutputStream() { override fun write(b: Int) { throw java.io.IOException("full") } }
        } }.isFailure)
    }

    @Test fun legacyNotesSurviveMigrationFailureAndRetry() {
        val name = "memory-test-" + UUID.randomUUID()
        fun open() = Room.databaseBuilder(context, MemoryDatabase::class.java, name)
            .addMigrations(MemoryDatabase.encryptExisting).build()
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
                legacy.execSQL("CREATE TABLE Memory(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, note TEXT NOT NULL)")
                legacy.execSQL("INSERT INTO Memory VALUES (3, 'first'), (9, 'second')")
                legacy.execSQL("CREATE TRIGGER simulatedFailure BEFORE UPDATE ON Memory WHEN OLD.id = 9 BEGIN SELECT RAISE(ABORT, 'interrupted migration'); END")
                legacy.version = 1
            }
            val interrupted = open()
            try { assertTrue(runCatching { interrupted.notes().recent() }.isFailure) }
            finally { interrupted.close() }

            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
                assertEquals(1, legacy.version)
                legacy.rawQuery("SELECT note FROM Memory WHERE id=3", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("first", it.getString(0))
                }
                legacy.execSQL("DROP TRIGGER simulatedFailure")
            }
            val migrated = open()
            try {
                assertEquals(listOf("second","first"), migrated.notes().recent())
                migrated.notes().add(Memory(note = "second"))
                migrated.openHelper.readableDatabase.query("SELECT id,note FROM Memory ORDER BY id").use { rows ->
                    val ids = mutableListOf<Long>()
                    val stored = mutableSetOf<String>()
                    while (rows.moveToNext()) {
                        ids += rows.getLong(0)
                        val value = rows.getString(1)
                        assertFalse(value in listOf("first", "second"))
                        assertTrue(stored.add(value))
                    }
                    assertEquals(listOf(3L,9L,10L), ids)
                }
                val notes = migrated.notes()
                val selected = requireNotNull(notes.snapshot(9))
                assertTrue(notes.correct(selected, "corrected note"))
                assertFalse(notes.correct(selected, "stale edit"))
                assertEquals(0, notes.deleteIfCurrent(selected.id, selected.note))
                val reviewed = requireNotNull(notes.snapshot(9))
                assertEquals(1, notes.deleteIfCurrent(reviewed.id, reviewed.note))
                assertEquals(listOf("second", "first"), notes.recent())
                migrated.openHelper.writableDatabase.execSQL("UPDATE Memory SET note='invalid' WHERE id=3")
                assertTrue(runCatching { migrated.notes().recent() }.isFailure)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
