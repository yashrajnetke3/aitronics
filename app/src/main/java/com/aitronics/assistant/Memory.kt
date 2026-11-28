package com.aitronics.assistant

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Entity
data class Memory(@PrimaryKey(autoGenerate = true) val id: Long = 0, val note: String)

@Dao
abstract class MemoryDao {
    fun add(memory: Memory) = insertCiphertext(memory.copy(note = NoteCipher.encrypt(memory.note)))
    fun recent(): List<String> = ciphertext().map(NoteCipher::decrypt)
    @Insert protected abstract fun insertCiphertext(memory: Memory)
    @Query("SELECT note FROM Memory ORDER BY id DESC LIMIT 50") protected abstract fun ciphertext(): List<String>
    @Query("DELETE FROM Memory") abstract fun clear()
}

@Database(entities = [Memory::class], version = 2, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun notes(): MemoryDao
    companion object {
        internal val encryptExisting = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room wraps migration in a transaction. Advance by ID so updated rows
                // cannot change the result set and only one plaintext row is in memory.
                var last: Long? = null
                while (true) {
                    val query = if (last == null) db.query("SELECT id, note FROM Memory ORDER BY id LIMIT 1")
                        else db.query("SELECT id, note FROM Memory WHERE id > ? ORDER BY id LIMIT 1", arrayOf(last))
                    val row = query.use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null } ?: break
                    db.execSQL("UPDATE Memory SET note = ? WHERE id = ?", arrayOf(NoteCipher.encrypt(row.second), row.first))
                    last = row.first
                }
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        @Volatile private var instance: MemoryDatabase? = null
        fun get(context: Context): MemoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MemoryDatabase::class.java, "assistant-memory").addMigrations(encryptExisting).build().also { instance = it }
        }
    }
}
