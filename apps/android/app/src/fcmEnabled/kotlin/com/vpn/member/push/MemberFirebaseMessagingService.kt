package com.vpn.member.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vpn.member.notification.UserNotificationCoordinator

private const val TAG = "MemberFcmService"

/** 接收运营/充值等远程推送，转交 [UserNotificationCoordinator]。 */
class MemberFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) {
            message.notification?.let { n ->
                UserNotificationCoordinator.showRemotePush(
                    context = applicationContext,
                    title = n.title ?: "跨云 VPN",
                    body = n.body ?: "",
                    navRoute = null,
                    dedupeKey = "fcm:${message.messageId ?: System.currentTimeMillis()}",
                )
            }
            return
        }
        UserNotificationCoordinator.showRemotePush(
            context = applicationContext,
            title = data["title"] ?: message.notification?.title ?: "跨云 VPN",
            body = data["body"] ?: message.notification?.body ?: "",
            navRoute = data["nav_route"],
            dedupeKey = data["dedupe_key"] ?: "fcm:${data["type"] ?: "generic"}",
        )
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "fcm token refreshed")
        FcmPushBootstrap.onTokenRefreshed(applicationContext, token)
    }
}
