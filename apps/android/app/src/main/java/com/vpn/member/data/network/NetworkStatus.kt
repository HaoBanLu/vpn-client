package com.vpn.member.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vpn.member.data.network.ApiRequestSupport

object NetworkStatus {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

const val NO_NETWORK_MESSAGE = "当前无网络连接，请检查网络后重试"

fun mapLoadError(error: Throwable): String? {
    if (ApiErrors.shouldSuppressPageError(error)) return null
    return ApiRequestSupport.mapError(error, "加载失败，请稍后重试")
}
