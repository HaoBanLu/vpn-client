package com.vpn.member.debug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

object AppDebugLogger {
    private const val MAX_LOCAL = 200

    private val idSeq = AtomicLong(0)
    private val _entries = MutableStateFlow<List<AppDebugLogEntry>>(emptyList())
    val entries: StateFlow<List<AppDebugLogEntry>> = _entries.asStateFlow()

    @Volatile
    private var enabled: Boolean = false

    private var uploadScope: CoroutineScope? = null
    private var uploader: (suspend (List<AppDebugLogEntry>) -> Unit)? = null
    private val pendingUpload = ArrayDeque<AppDebugLogEntry>()

    fun configure(
        enabled: Boolean,
        scope: CoroutineScope,
        upload: suspend (List<AppDebugLogEntry>) -> Unit,
    ) {
        this.enabled = enabled
        uploadScope = scope
        uploader = upload
        if (!enabled) {
            _entries.value = emptyList()
            pendingUpload.clear()
        }
    }

    fun isEnabled(): Boolean = enabled

    fun info(category: String, message: String, context: Map<String, String> = emptyMap()) {
        log("info", category, message, context)
    }

    fun warn(category: String, message: String, context: Map<String, String> = emptyMap()) {
        log("warn", category, message, context)
    }

    fun error(category: String, message: String, context: Map<String, String> = emptyMap()) {
        log("error", category, message, context)
    }

    fun log(level: String, category: String, message: String, context: Map<String, String> = emptyMap()) {
        if (!enabled) return
        val sanitized = sanitize(message)
        if (sanitized.isBlank()) return
        val entry =
            AppDebugLogEntry(
                id = idSeq.incrementAndGet(),
                level = level,
                category = category,
                message = sanitized,
                context = context.mapValues { sanitize(it.value) },
                clientAt = Instant.now().toString(),
            )
        _entries.value = (_entries.value + entry).takeLast(MAX_LOCAL)
        queueUpload(entry, immediate = level == "error" || level == "warn")
    }

    fun flush() {
        if (!enabled) return
        flushPending(immediate = true)
    }

    private fun queueUpload(entry: AppDebugLogEntry, immediate: Boolean) {
        synchronized(pendingUpload) {
            pendingUpload.addLast(entry)
        }
        flushPending(immediate)
    }

    private fun flushPending(immediate: Boolean) {
        val scope = uploadScope ?: return
        val upload = uploader ?: return
        scope.launch {
            if (!immediate) {
                kotlinx.coroutines.delay(30_000)
            }
            val batch =
                synchronized(pendingUpload) {
                    if (pendingUpload.isEmpty()) return@launch
                    pendingUpload.toList().also { pendingUpload.clear() }
                }
            runCatching { upload(batch) }
        }
    }

    private fun sanitize(raw: String): String {
        var text = raw.trim()
        if (text.length > 2000) {
            text = text.take(2000) + "…"
        }
        val patterns =
            listOf(
                Regex("(?i)(bearer\\s+)[a-z0-9._\\-]+"),
                Regex("(?i)(token[=:]\\s*)[a-z0-9._\\-]+"),
                Regex("(?i)(password[=:]\\s*)\\S+"),
                Regex("eyJ[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+"),
            )
        patterns.forEach { re ->
            text = text.replace(re, "$1[redacted]")
        }
        return text
    }
}
