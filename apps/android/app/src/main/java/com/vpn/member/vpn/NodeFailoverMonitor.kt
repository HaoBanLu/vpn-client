package com.vpn.member.vpn

/**
 * 周期探测连续失败计数。
 * 自动同区 failover 默认关闭：弱网下 HTTP 探针抖动 ≠ 隧道坏，自动切节点会放大「不稳」体感。
 */
object NodeFailoverMonitor {
    private const val FAIL_THRESHOLD = 3

    /** 关闭后 [shouldFailover] 恒为 false；仍可记录成败供诊断。 */
    const val AUTO_FAILOVER_ENABLED = false

    @Volatile
    private var consecutiveFails = 0

    fun recordFailure() {
        consecutiveFails++
    }

    fun recordSuccess() {
        consecutiveFails = 0
    }

    fun shouldFailover(): Boolean =
        AUTO_FAILOVER_ENABLED && consecutiveFails >= FAIL_THRESHOLD

    fun reset() {
        consecutiveFails = 0
    }

    /** 测试用：读取当前连续失败次数。 */
    internal fun consecutiveFailsForTest(): Int = consecutiveFails
}
