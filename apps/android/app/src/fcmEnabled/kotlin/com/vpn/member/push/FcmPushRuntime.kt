package com.vpn.member.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "FcmPushRuntime"

/** FCM 已启用：拉取 Token 并上报后端。 */
object FcmPushRuntime {
    fun start(
        context: Context,
        scope: CoroutineScope,
        uploadToken: suspend (String) -> Unit,
    ) {
        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                if (token.isNotBlank()) {
                    uploadToken(token)
                }
            }.onFailure { e ->
                Log.w(TAG, "fetch fcm token failed", e)
            }
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result ?: return@addOnCompleteListener
            scope.launch {
                runCatching { uploadToken(token) }
                    .onFailure { e -> Log.w(TAG, "upload fcm token failed", e) }
            }
        }
    }
}
