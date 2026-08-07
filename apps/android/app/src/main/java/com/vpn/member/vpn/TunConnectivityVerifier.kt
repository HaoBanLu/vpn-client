package com.vpn.member.vpn

import android.util.Log
import com.vpn.member.debug.AppDebugLogger
import kotlinx.coroutines.delay

/** 隧道建立后做最小可达性校验，避免 UI 误报「已连接」。 */
object TunConnectivityVerifier {
    private const val TAG = "TunConnectivityVerifier"
    const val DEFAULT_SETTLE_MS = 400L
    const val DEFAULT_MAX_ATTEMPTS = 3
    const val DEFAULT_RETRY_DELAY_MS = 2_000L

    /**
     * @param splitDomesticDirect split 分流时国内站点走物理网，海外经 Mihomo mixed-port。
     * @param settleMs 每次探测前等待（海外回国可加长，见 [PostConnectVerifyPolicy]）。
     */
    suspend fun verifyOrThrow(
        splitDomesticDirect: Boolean = false,
        domesticReturn: Boolean = false,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
        settleMs: Long = DEFAULT_SETTLE_MS,
    ) {
        var lastError: Exception? = null
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            try {
                verifyOnce(splitDomesticDirect, domesticReturn, settleMs)
                if (attempt > 0) {
                    AppDebugLogger.info(
                        category = "mihomo",
                        message = "隧道探测重试后通过",
                        context = mapOf("attempt" to (attempt + 1).toString()),
                    )
                }
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "verify attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
                if (attempt < maxAttempts - 1) {
                    delay(retryDelayMs)
                }
            }
        }
        throw lastError ?: error("android: tunnel verify failed")
    }

    private suspend fun verifyOnce(
        splitDomesticDirect: Boolean,
        domesticReturn: Boolean,
        settleMs: Long = DEFAULT_SETTLE_MS,
    ) {
        delay(settleMs.coerceAtLeast(0L))

        val basicOk =
            when {
                splitDomesticDirect -> {
                    val physical = ConnectivityProbe.findPhysicalNetwork()
                    if (physical != null) {
                        ConnectivityProbe.probeBasicOnNetwork(physical)
                    } else {
                        ConnectivityProbe.probeBasicOnNetworkDirect()
                    }
                }
                domesticReturn -> MihomoLocalProbe.isDomesticReachable()
                else -> MihomoLocalProbe.isOverseasReachable()
            }

        if (!basicOk) {
            Log.w(TAG, "basic connectivity failed split=$splitDomesticDirect domesticReturn=$domesticReturn")
            AppDebugLogger.warn(
                category = "mihomo",
                message = if (domesticReturn) "回国隧道基础连通性失败" else "隧道基础连通性失败",
                context =
                    mapOf(
                        "split" to splitDomesticDirect.toString(),
                        "domestic_return" to domesticReturn.toString(),
                    ),
            )
            error("android: tunnel verify failed (no network)")
        }

        val latencyMs =
            when {
                domesticReturn -> MihomoLocalProbe.measureDomesticLatency()
                else -> MihomoLocalProbe.measureOverseasLatency()
            }
        if (latencyMs == null) {
            if (splitDomesticDirect) {
                VpnDiag.warn(
                    "overseas_probe",
                    "split 模式海外不可达，国内直连仍可用",
                    mapOf("split" to "true"),
                )
                AppDebugLogger.warn(
                    category = "mihomo",
                    message = "split 模式海外探测失败（仍允许连接）",
                )
                return
            }
            Log.w(TAG, "mihomo mixed-port overseas probe failed split=$splitDomesticDirect")
            AppDebugLogger.warn(
                category = "mihomo",
                message = "代理节点探测失败（mixed-port）",
                context = mapOf("split" to splitDomesticDirect.toString()),
            )
            error("android: tunnel verify failed (proxy unreachable)")
        }
        AppDebugLogger.info(
            category = "mihomo",
            message = "隧道探测通过",
            context = mapOf("latency_ms" to latencyMs.toString(), "split" to splitDomesticDirect.toString()),
        )
    }
}
