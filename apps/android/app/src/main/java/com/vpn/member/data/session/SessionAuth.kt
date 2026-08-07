package com.vpn.member.data.session

import com.vpn.member.data.local.TokenStore
import org.json.JSONObject

/**
 * 统一判断并处理会员端会话失效（401 / 被踢 / 过期）。
 */
object SessionAuth {
    private val sessionAppCodes =
        setOf(
            "SESSION_REVOKED",
            "LOGIN_ON_ANOTHER_DEVICE",
        )

    /** 登录/注册等公开接口的 401 表示凭证错误，不应触发全局登出。 */
    private val publicAuthPathMarkers =
        listOf(
            "/auth/login",
            "/auth/register",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/email-code/",
        )

    fun shouldInvalidateSession(path: String, hadAuth: Boolean, appCode: String?): Boolean {
        if (appCode in sessionAppCodes) return true
        if (publicAuthPathMarkers.any { path.contains(it) }) return false
        return hadAuth
    }

    fun parseAppCode(body: String): String? =
        runCatching { JSONObject(body).optString("app_code").takeIf { it.isNotBlank() } }.getOrNull()

    fun parseMessage(body: String): String? =
        runCatching { JSONObject(body).optString("message").takeIf { it.isNotBlank() } }.getOrNull()

    fun sessionInvalidationMessage(body: String, appCode: String?): String {
        val raw = parseMessage(body)
        return when (appCode) {
            "LOGIN_ON_ANOTHER_DEVICE" -> raw?.ifBlank { null } ?: "账号已在其他设备登录，请重新登录"
            "SESSION_REVOKED" -> raw?.ifBlank { null } ?: "登录状态已失效，请重新登录"
            else ->
                when (raw?.trim()?.lowercase()) {
                    null, "" -> "登录状态已失效，请重新登录"
                    "token expired",
                    "invalid token",
                    "invalid session",
                    "unauthorized",
                    "authorization header required",
                    "invalid authorization header format",
                    -> "登录状态已失效，请重新登录"
                    else -> raw
                }
        }
    }

    /**
     * @return 是否触发了会话失效流程
     */
    fun invalidateIfNeeded(
        tokenStore: TokenStore,
        path: String,
        hadAuth: Boolean,
        body: String,
        appCode: String? = parseAppCode(body),
    ): Boolean {
        if (!shouldInvalidateSession(path, hadAuth, appCode)) return false
        if (tokenStore.getJwt().isNullOrBlank()) return false
        val message = sessionInvalidationMessage(body, appCode)
        tokenStore.clearSession()
        SessionEvents.publish(message, appCode)
        return true
    }
}
