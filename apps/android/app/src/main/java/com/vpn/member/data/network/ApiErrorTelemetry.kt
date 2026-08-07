package com.vpn.member.data.network

import com.vpn.member.data.repository.AppException
import java.util.concurrent.atomic.AtomicLong

/**
 * 控制面 API 失败采样（内存环形缓冲），供调试页与诊断日志 category=api_error 对齐。
 * 不持久化到磁盘，避免隐私与存储膨胀。
 */
object ApiErrorTelemetry {
    private const val MAX_RECENT = 50

    data class Snapshot(
        val totalFailures: Long,
        val retryableFailures: Long,
        val recent: List<Entry>,
    )

    data class Entry(
        val atMillis: Long,
        val endpoint: String,
        val errorType: String,
        val appCode: String?,
        val retryable: Boolean,
        val message: String,
    )

    private val totalFailures = AtomicLong(0)
    private val retryableFailures = AtomicLong(0)
    private val recent = ArrayDeque<Entry>()

    @Synchronized
    fun record(
        endpoint: String,
        error: Throwable,
        succeeded: Boolean = false,
    ) {
        if (succeeded) return
        totalFailures.incrementAndGet()
        val app = error as? AppException
        val retryable = app?.retryable == true || ApiRequestSupport.isRetryable(error)
        if (retryable) {
            retryableFailures.incrementAndGet()
        }
        val entry =
            Entry(
                atMillis = System.currentTimeMillis(),
                endpoint = endpoint,
                errorType = error.javaClass.simpleName,
                appCode = app?.appCode,
                retryable = retryable,
                message = (app?.userMessage ?: error.message.orEmpty()).take(160),
            )
        recent.addFirst(entry)
        while (recent.size > MAX_RECENT) {
            recent.removeLast()
        }
    }

    @Synchronized
    fun snapshot(): Snapshot =
        Snapshot(
            totalFailures = totalFailures.get(),
            retryableFailures = retryableFailures.get(),
            recent = recent.toList(),
        )

    @Synchronized
    fun resetForTest() {
        totalFailures.set(0)
        retryableFailures.set(0)
        recent.clear()
    }
}
