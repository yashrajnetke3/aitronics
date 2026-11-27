package com.aitronics.assistant

import android.content.Context
import androidx.room.*
import java.util.concurrent.Executors

@Entity
data class Memory(@PrimaryKey(autoGenerate = true) val id: Long = 0, val note: String)

@Dao
interface MemoryDao {
    @Insert fun add(memory: Memory)
    @Query("SELECT note FROM Memory ORDER BY id DESC LIMIT 50") fun recent(): List<String>
    @Query("DELETE FROM Memory") fun clear()
}

@Database(entities = [Memory::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun notes(): MemoryDao
    companion object {
        val executor = Executors.newSingleThreadExecutor()
        @Volatile private var instance: MemoryDatabase? = null
        fun get(context: Context): MemoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MemoryDatabase::class.java, "assistant-memory").build().also { instance = it }
        }
    }
}
