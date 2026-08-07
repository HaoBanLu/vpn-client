package com.vpn.member.push

import android.content.Context
import com.vpn.member.BuildConfig
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object FcmPushBootstrap {
    @Volatile
    private var uploadBlock: (suspend (String) -> Unit)? = null

    @Volatile
    private var appScope: CoroutineScope? = null

    fun start(
        context: Context,
        scope: CoroutineScope,
        uploadToken: suspend (String) -> Unit,
    ) {
        if (!BuildConfig.FCM_ENABLED) return
        uploadBlock = uploadToken
        appScope = scope
        FcmPushRuntime.start(context, scope, uploadToken)
    }

    fun onTokenRefreshed(context: Context, token: String) {
        if (!BuildConfig.FCM_ENABLED) return
        val scope = appScope ?: return
        val upload = uploadBlock ?: return
        scope.launch {
            runCatching { upload(token) }
        }
    }

    fun refreshAfterLogin(repository: AppRepository) {
        if (!BuildConfig.FCM_ENABLED) return
        val scope = appScope ?: return
        FcmPushRuntime.start(
            repository.applicationContext(),
            scope,
        ) { token -> repository.syncPushToken(token) }
    }
}
