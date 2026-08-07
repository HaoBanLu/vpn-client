package com.vpn.member.vpn

/**
 * 连上前硬门禁的探测重试策略。
 *
 * 防假连要求首连必须过探测，但海外回国（缅甸等 → 经港 Reality → 国内落地）握手更慢，
 * 禁止用过短首连重试把「其实可达」打成连接失败（3.11 回归）。
 */
object PostConnectVerifyPolicy {
    data class Params(
        val maxAttempts: Int,
        val retryDelayMs: Long,
        val settleMs: Long,
    )

    /** 国内/默认首连与后台复检：与 [TunConnectivityVerifier] 默认一致。 */
    val DEFAULT =
        Params(
            maxAttempts = TunConnectivityVerifier.DEFAULT_MAX_ATTEMPTS,
            retryDelayMs = TunConnectivityVerifier.DEFAULT_RETRY_DELAY_MS,
            settleMs = TunConnectivityVerifier.DEFAULT_SETTLE_MS,
        )

    /**
     * 海外时区 + 回国 profile：加长 settle、增加次数与间隔，给 Reality 握手窗口。
     * 最坏约数十秒，仍远快于历史 ~70s 全超时；国内首连不受影响。
     */
    val OVERSEAS_DOMESTIC_RETURN_INITIAL =
        Params(
            maxAttempts = 4,
            retryDelayMs = 2_500L,
            settleMs = 1_200L,
        )

    fun resolve(
        initialConnectPhase: Boolean,
        domesticReturn: Boolean,
        overseasTimezone: Boolean = OverseasLocaleHint.isOverseasTimezone(),
    ): Params {
        if (!initialConnectPhase) return DEFAULT
        if (domesticReturn && overseasTimezone) return OVERSEAS_DOMESTIC_RETURN_INITIAL
        return DEFAULT
    }
}
