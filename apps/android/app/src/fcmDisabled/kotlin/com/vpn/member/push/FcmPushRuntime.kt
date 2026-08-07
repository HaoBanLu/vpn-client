package com.vpn.member.push

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/** FCM 未启用时的空实现（无 google-services.json）。 */
object FcmPushRuntime {
    fun start(
        context: Context,
        scope: CoroutineScope,
        uploadToken: suspend (String) -> Unit,
    ) {
        // no-op
    }
}
