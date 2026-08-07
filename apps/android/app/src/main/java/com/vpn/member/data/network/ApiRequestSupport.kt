package com.vpn.member.data.network

import com.google.gson.JsonParseException
import com.vpn.member.data.repository.AppException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLException
import kotlin.random.Random

/**
 * 全 App 控制面 API 共用：可重试判断、退避重试、错误文案与 trace_id。
 * 鉴权流程在 [com.vpn.member.data.auth.AuthRequestSupport] 上叠加 VPN 预处理。
 */
object ApiRequestSupport {
    const val DEFAULT_MAX_ATTEMPTS = 2

    suspend fun <T> withRetry(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        retryDelayMs: ((attempt: Int) -> Long)? = null,
        onRetry: ((attempt: Int, error: Throwable) -> Unit)? = null,
        block: suspend () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            runCatching { block() }
                .onSuccess { return it }
                .onFailure { err ->
                    lastError = err
                    val shouldRetry = attempt < maxAttempts - 1 && isRetryable(err)
                    if (shouldRetry) {
                        val nextAttempt = attempt + 1
                        onRetry?.invoke(nextAttempt, err)
                        delay(retryDelayMs?.invoke(nextAttempt) ?: defaultRetryDelayMs(nextAttempt))
                    } else {
                        throw err
                    }
                }
        }
        throw lastError ?: IllegalStateException("api request failed")
    }

    /** PRD 退避：300ms → 900ms，带随机抖动。 */
    fun defaultRetryDelayMs(attempt: Int): Long {
        val base = if (attempt <= 1) 300L else 900L
        return base + Random.nextLong(0, 201)
    }

    fun isRetryable(error: Throwable): Boolean {
        val app = error as? AppException
        if (app?.retryable == true) return true
        return when (error) {
            is SSLException -> false
            is UnknownHostException,
            is NoRouteToHostException,
            is ConnectException,
            is SocketTimeoutException,
            is IOException,
            -> true
            else -> false
        }
    }

    fun mapError(
        e: Throwable,
        fallback: String,
        serverBaseUrl: String? = null,
    ): String {
        (e as? AppException)?.let { app ->
            val base =
                when (app.appCode) {
                    "LOGIN_DENIED_NEW_DEVICE" -> app.userMessage
                    "LOGIN_ON_ANOTHER_DEVICE" -> app.userMessage
                    "SESSION_REVOKED" -> app.userMessage
                    else -> app.userMessage.ifBlank { fallback }
                }
            return appendTraceId(base, app.traceId)
        }
        val serverHint = formatServerHint(serverBaseUrl)
        return when (e) {
            is JsonParseException -> "服务器响应异常，请稍后重试"
            is UnknownHostException ->
                if (serverHint != null) {
                    "无法解析服务器地址（$serverHint），请检查接口域名或网络"
                } else {
                    "无法解析服务器地址，请检查网络或接口域名配置"
                }
            is NoRouteToHostException ->
                if (serverHint != null) {
                    "无法到达服务器（$serverHint），请切换 Wi-Fi/移动数据后重试"
                } else {
                    "无法到达服务器网络，请切换网络后重试"
                }
            is ConnectException ->
                if (serverHint != null) {
                    "无法连接服务器（$serverHint），请确认服务已启动且手机能访问该地址"
                } else {
                    "服务器连接失败，请确认服务状态或稍后重试"
                }
            is SocketTimeoutException ->
                if (serverHint != null) {
                    "连接服务器超时（$serverHint），请稍后重试"
                } else {
                    "连接超时，请稍后重试"
                }
            is SSLHandshakeException -> "安全握手失败，请检查系统时间或网络环境"
            is SSLException -> "安全连接失败，请检查系统时间或网络环境"
            is IOException -> mapIOExceptionMessage(e, serverHint)
            else -> fallback
        }
    }

    private fun mapIOExceptionMessage(
        e: IOException,
        serverHint: String?,
    ): String {
        val raw = e.message.orEmpty()
        if (raw.contains("Cleartext HTTP traffic", ignoreCase = true) ||
            raw.contains("cleartext", ignoreCase = true)
        ) {
            return if (serverHint != null) {
                "系统拦截了 HTTP 明文请求，无法访问 $serverHint。请更新 App 网络安全配置或改用 HTTPS"
            } else {
                "系统拦截了 HTTP 明文请求，请更新 App 配置或改用 HTTPS"
            }
        }
        return if (serverHint != null) {
            "网络请求失败，无法连接 $serverHint，请检查手机网络或服务器端口是否开放"
        } else {
            "网络请求失败，请检查网络后重试"
        }
    }

    private fun formatServerHint(serverBaseUrl: String?): String? {
        val trimmed = serverBaseUrl?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed.removeSuffix("/")
    }

    fun buildErrorContext(error: Throwable): Map<String, String> {
        val app = error as? AppException
        val context = mutableMapOf<String, String>()
        context["error_type"] = error.javaClass.simpleName
        app?.appCode?.takeIf { it.isNotBlank() }?.let { context["app_code"] = it }
        app?.traceId?.takeIf { it.isNotBlank() }?.let { context["trace_id"] = it }
        app?.retryable?.let { context["retryable"] = it.toString() }
        val msg = error.message.orEmpty().take(200)
        if (msg.isNotBlank()) {
            context["raw_message"] = msg
        }
        return context
    }

    private fun appendTraceId(message: String, traceId: String?): String {
        if (traceId.isNullOrBlank()) return message
        return "$message（追踪ID: $traceId）"
    }
}
