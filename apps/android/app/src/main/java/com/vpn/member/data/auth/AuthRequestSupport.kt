package com.vpn.member.data.auth

import com.vpn.member.BuildConfig
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.vpn.VpnController

/**
 * 鉴权控制面请求：在 [ApiRequestSupport] 基础上增加 VPN/Kill Switch 预处理。
 */
object AuthRequestSupport {
    const val DEFAULT_MAX_ATTEMPTS = ApiRequestSupport.DEFAULT_MAX_ATTEMPTS

    fun prepareControlPlaneRequest(vpnController: VpnController) {
        vpnController.disconnectForAuth()
        vpnController.releaseKillSwitch()
    }

    suspend fun <T> withRetry(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        retryDelayMs: ((attempt: Int) -> Long)? = null,
        onRetry: ((attempt: Int, error: Throwable) -> Unit)? = null,
        block: suspend () -> T,
    ): T =
        ApiRequestSupport.withRetry(
            maxAttempts = maxAttempts,
            retryDelayMs = retryDelayMs,
            onRetry = onRetry,
            block = block,
        )

    fun isRetryable(error: Throwable): Boolean = ApiRequestSupport.isRetryable(error)

    /** 登录/注册等鉴权接口：附带当前编译的服务器地址，便于排查连不上问题。 */
    fun mapError(e: Throwable, fallback: String): String =
        ApiRequestSupport.mapError(
            e,
            fallback,
            serverBaseUrl = BuildConfig.API_BASE_URL,
        )

    fun buildErrorContext(error: Throwable): Map<String, String> = ApiRequestSupport.buildErrorContext(error)
}
