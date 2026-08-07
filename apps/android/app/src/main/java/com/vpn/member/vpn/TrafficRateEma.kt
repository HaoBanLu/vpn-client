package com.vpn.member.vpn

/** 实时速率指数滑动平均，减轻 0 ↔ 跳变。 */
object TrafficRateEma {
    /** 约 3～4 次 1s 采样达到稳态。 */
    const val DEFAULT_ALPHA = 0.35

    fun smooth(previous: Long, instant: Long, alpha: Double = DEFAULT_ALPHA): Long {
        val safeInstant = instant.coerceAtLeast(0L)
        if (previous <= 0L) return safeInstant
        val blended = alpha * safeInstant + (1.0 - alpha) * previous
        return blended.toLong().coerceAtLeast(0L)
    }
}
