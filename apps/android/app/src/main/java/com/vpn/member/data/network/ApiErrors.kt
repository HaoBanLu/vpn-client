package com.vpn.member.data.network

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 已触发自动登出，UI 层无需再展示错误卡片。 */
class SessionInvalidatedException : IOException("session invalidated")

object ApiErrors {
    const val UNREACHABLE_APP_CODE = "API_UNREACHABLE"
    const val UNREACHABLE_MESSAGE = "无法连接服务器，请检查网络后重试"

    /**
     * 判断是否为「接口域名/地址根本不可达」类错误（用于日志或提示分级）。
     * **不会**据此自动登出；仅 401 会话失效才清 token。
     */
    fun isHardApiFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is UnknownHostException,
                is ConnectException,
                is SSLException,
                -> return true
                is IOException -> {
                    val msg = current.message.orEmpty().lowercase()
                    if (
                        msg.contains("unable to resolve host") ||
                        msg.contains("failed to connect") ||
                        msg.contains("connection refused")
                    ) {
                        return true
                    }
                }
            }
            current = current.cause
        }
        return false
    }

    /** UI 层：全局登出类错误，无需再展示页内红字。 */
    fun shouldSuppressPageError(error: Throwable): Boolean =
        error is SessionInvalidatedException
}
