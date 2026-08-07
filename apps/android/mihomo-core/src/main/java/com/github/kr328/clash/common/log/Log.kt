package com.github.kr328.clash.common.log

import android.util.Log as AndroidLog

object Log {
    private const val TAG = "Mihomo"

    fun d(message: String) {
        AndroidLog.d(TAG, message)
    }

    fun i(message: String) {
        AndroidLog.i(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        AndroidLog.e(TAG, message, throwable)
    }
}
