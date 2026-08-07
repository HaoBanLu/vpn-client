package com.vpn.member.data.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SessionInvalidation(
    val message: String,
    val appCode: String? = null,
)

object SessionEvents {
    private val _invalidated = MutableSharedFlow<SessionInvalidation>(extraBufferCapacity = 1)
    val invalidated = _invalidated.asSharedFlow()

    fun publish(message: String, appCode: String? = null) {
        _invalidated.tryEmit(SessionInvalidation(message, appCode))
    }
}
