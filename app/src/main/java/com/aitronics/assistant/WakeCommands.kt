package com.aitronics.assistant

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager

object WakeCommands {
    private val names = listOf("aitronics", "electronics", "i tronics", "a tronics", "eye tronics", "i tronix", "eye tronix", "i tron ics", "a tron ics", "i tronic", "i tron x", "draw next", "i try next", "trunks", "eye tronics")
    fun afterWake(text: String, phrase: String): String? {
        val prefixes = mutableListOf(phrase.trim())
        if (phrase.equals("hey aitronics", true)) {
            for (name in names) for (hi in listOf("hey", "hay", "hi", "he", "a")) prefixes += "$hi $name"
        }
        if (phrase.equals("hey aitronics",true) || phrase.equals("hey",true)) prefixes += listOf("hey","hay")
        val words = text.trim().trimEnd('.', '!', '?')
        for (prefix in prefixes.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
            if (words.equals(prefix,true)) return ""
            if (words.startsWith(prefix,true) && words.getOrNull(prefix.length) in listOf(' ',',')) return words.drop(prefix.length).trimStart(' ', ',')
        }
        return null
    }
    fun normalize(text: String, phrase: String): String {
        var s = afterWake(text, phrase) ?: text.trim()
        s = s.replace(Regex("""^(?:(?:hey|hay|electronics)[ ,]+)(?=(?:open|turn|switch|close|start)\b)""",RegexOption.IGNORE_CASE), "").trimEnd('.', '!', '?')
        val light = Regex("""(?:please )?(open|close|switch on|turn on|switch off|turn off) (?:the |my )?(?:flashlight|flash light|light|torch)(?: please)?""",RegexOption.IGNORE_CASE).matchEntire(s)
        if (light != null) return if (light.groupValues[1].lowercase() in listOf("open","switch on","turn on")) "turn on flashlight" else "turn off flashlight"
        return when(s.lowercase()) {
            "open teligaram","open teligram","open tele gram","open telegraph" -> "open Telegram"
            "open whatapp","open whats app","open whats up","open what app","open what sap","open what’s app" -> "open WhatsApp"
            "scroll dow","swipe up","scroll it down" -> "scroll down"
            "swipe down","scroll it up" -> "scroll up"
            "read written message","read my message","read the draft","read written massage","read my draft","read message" -> "read draft"
            "send","send it","send the message","send massage" -> "send message"
            "light on","torch on","flashlight on" -> "turn on flashlight"
            "light off","torch off","flashlight off" -> "turn off flashlight"
            "open the camera","start camera","launch camera" -> "open camera"
            else -> s
        }
    }
    fun local(context: Context, command: String): String? {
        return when(command.lowercase()) {
            "turn on flashlight","turn off flashlight" -> {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it)[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true } ?: return "No flashlight available."
                val on = command.equals("turn on flashlight",true)
                manager.setTorchMode(id,on)
                if(on) "Flashlight on." else "Flashlight off."
            }
            "battery level","battery status" -> {
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                "Battery level: "+battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)+" percent."
            }
            "volume up","volume down","mute" -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val direction = when(command.lowercase()) { "volume up" -> AudioManager.ADJUST_RAISE; "volume down" -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_MUTE }
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,direction,AudioManager.FLAG_SHOW_UI)
                "Media volume updated."
            }
            "time","what time is it" -> java.text.DateFormat.getTimeInstance().format(java.util.Date())
            "date","what is the date" -> java.text.DateFormat.getDateInstance().format(java.util.Date())
            else -> null
        }
    }
}
