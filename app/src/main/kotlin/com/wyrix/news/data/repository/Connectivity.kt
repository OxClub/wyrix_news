package com.wyrix.news.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun interface Connectivity {
    fun isOnline(): Boolean
}

class AndroidConnectivity(context: Context) : Connectivity {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val cm = manager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
