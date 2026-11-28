package com.aitronics.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal object ChatRouting {
    fun useLocal(mode: String, hasCloudKey: Boolean, connected: () -> Boolean): Boolean =
        mode == "Offline" || !hasCloudKey || (mode == "Auto" && !connected())

    fun connected(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
