package com.aitronics.assistant

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.text.format.Formatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Only locally observable values belong in this registry. Resolve every step before reading. */
internal object DeviceReadings {
    private enum class Reading(vararg val phrases: String) {
        BATTERY("battery", "battery level", "battery status", "how much battery is left"),
        STORAGE("storage", "free storage", "available storage", "how much storage is free"),
        VOLUME("media volume", "volume level"),
        TIME("time", "what time is it"),
        DATE("date", "what is the date", "today's date")
    }

    private val aliases = Reading.entries.flatMap { r -> r.phrases.map { it to r } }.toMap()
    private val separator = Regex("""\s+(?:and then|then|and)\s+""")
    private val prefix = Regex("""^(?:(?:please )?(?:check|tell me|show me) )?(?:(?:my|the) )?""")

    fun answer(context: Context, input: String): String? {
        val parts = separator.split(input.trim().trimEnd('?','.').lowercase(Locale.ROOT), limit = 6)
        val steps = parts.map { aliases[it.replaceFirst(prefix, "")] }
        if (steps.first() == null) return null
        if (steps.size > 5) return "Ask for up to five device readings at once."
        if (steps.any { it == null }) return "This device check supports battery, storage, media volume, time and date. No steps ran."
        if (steps.distinct().size != steps.size) return "That device check repeats a reading. No steps ran."
        return steps.filterNotNull().joinToString("\n") { read(context, it) }
    }

    private fun read(context: Context, step: Reading): String = when (step) {
        Reading.BATTERY -> {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (value in 0..100) "Battery level: $value percent." else "Battery level unavailable."
        }
        Reading.STORAGE -> "Free storage: " + Formatter.formatFileSize(context, context.filesDir.usableSpace)
        Reading.VOLUME -> {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            "Media volume: " + audio.getStreamVolume(AudioManager.STREAM_MUSIC) + " of " +
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
        Reading.TIME -> DateFormat.getTimeInstance().format(Date())
        Reading.DATE -> DateFormat.getDateInstance().format(Date())
    }
}
