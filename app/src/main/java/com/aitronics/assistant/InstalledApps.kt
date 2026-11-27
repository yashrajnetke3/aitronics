package com.aitronics.assistant

import android.content.Context
import android.content.Intent
import java.util.Locale

fun openInstalledApp(context: Context, name: String): String {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val manager = context.packageManager
    fun normalize(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    val matches = manager.queryIntentActivities(launcher, 0).filter {
        normalize(it.loadLabel(manager).toString()) == normalize(name)
    }
    return when (matches.size) {
        0 -> "No installed app matched “$name”. Use its name from your app drawer."
        1 -> {
            val activity = matches.single().activityInfo
            context.startActivity(launcher.setClassName(activity.packageName, activity.name))
            "Opened " + matches.single().loadLabel(manager)
        }
        else -> {
            val intents = matches.map { launcher.cloneFilter().setClassName(it.activityInfo.packageName, it.activityInfo.name) }
            context.startActivity(Intent.createChooser(intents.first(), "Choose an app").putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray()))
            "Choose which app to open."
        }
    }
}
