package com.vpn.member.vpn

import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 部分国产 ROM（含小米）要求 VpnService.protect 在主线程调用，否则代理出站失败。 */
object VpnProtector {
    private const val TAG = "VpnProtector"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun protect(service: VpnService, fd: Int): Boolean {
        if (fd < 0) return false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return service.protect(fd)
        }
        val latch = CountDownLatch(1)
        var ok = false
        mainHandler.post {
            ok = runCatching { service.protect(fd) }.getOrDefault(false)
            if (!ok) Log.w(TAG, "protect failed fd=$fd")
            latch.countDown()
        }
        return runCatching { latch.await(2, TimeUnit.SECONDS) && ok }.getOrDefault(false)
    }
}
