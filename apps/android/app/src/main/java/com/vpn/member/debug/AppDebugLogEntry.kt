package com.vpn.member.debug

import java.time.Instant

data class AppDebugLogEntry(
    val id: Long = System.nanoTime(),
    val level: String,
    val category: String,
    val message: String,
    val context: Map<String, String> = emptyMap(),
    val clientAt: String = Instant.now().toString(),
)
