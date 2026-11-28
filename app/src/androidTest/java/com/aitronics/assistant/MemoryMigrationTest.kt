package com.aitronics.assistant

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MemoryMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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
                migrated.openHelper.writableDatabase.execSQL("UPDATE Memory SET note='invalid' WHERE id=3")
                assertTrue(runCatching { migrated.notes().recent() }.isFailure)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
