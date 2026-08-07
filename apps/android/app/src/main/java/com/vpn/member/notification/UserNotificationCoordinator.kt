package com.vpn.member.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vpn.member.MainActivityIntents
import com.vpn.member.R
import com.vpn.member.data.local.AuthDisconnectReasonStore
import com.vpn.member.data.local.LastInvalidationStore
import com.vpn.member.data.network.ApiErrors
import com.vpn.member.data.session.AppEvents
import com.vpn.member.data.session.RechargeStatusChange
import com.vpn.member.data.session.SessionEvents
import com.vpn.member.data.session.SessionInvalidation
import com.vpn.member.vpn.PrivacyForceDisconnectEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Application 级用户通知协调器：订阅会话/套餐/充值事件并发送系统通知。
 * 会话失效时在 Application 层断开 VPN，不依赖 MainActivity 是否存活。
 */
object UserNotificationCoordinator {
    const val EXTRA_NAV_ROUTE = "nav_route"

    private const val DEDUPE_WINDOW_MS = 5 * 60 * 1000L

    private lateinit var appContext: Context
    private lateinit var appScope: CoroutineScope
    private lateinit var lastInvalidationStore: LastInvalidationStore
    private var onSessionInvalidated: (() -> Unit)? = null
    private val recentDedupeKeys = LinkedHashMap<String, Long>()

    @Volatile
    private var started = false

    fun start(
        context: Context,
        scope: CoroutineScope,
        onSessionInvalidated: (() -> Unit)? = null,
    ) {
        // 允许更新 disconnect 回调（Application onCreate 后测试可补挂）
        this.onSessionInvalidated = onSessionInvalidated ?: this.onSessionInvalidated
        if (started) return
        started = true
        appContext = context.applicationContext
        appScope = scope
        lastInvalidationStore = LastInvalidationStore(appContext)
        ensureChannels()
        SessionEvents.invalidated
            .onEach { dispatchSessionInvalidation(it) }
            .launchIn(scope)
        PrivacyForceDisconnectEvents.events
            .onEach { dispatchForceDisconnect(it) }
            .launchIn(scope)
        AppEvents.rechargeStatusChanged
            .onEach { dispatchRechargeChange(it) }
            .launchIn(scope)
    }

    fun notifyRechargeChange(change: RechargeStatusChange) {
        if (!started) return
        dispatchRechargeChange(change)
    }

    /** FCM 远程推送（P2-5）：数据消息或 notification 消息统一入口。 */
    fun showRemotePush(
        context: Context,
        title: String,
        body: String,
        navRoute: String?,
        dedupeKey: String,
    ) {
        if (!started) return
        postNotification(
            UserNotificationPayload(
                channelId = UserNotificationChannels.REMOTE_PUSH,
                notificationId = UserNotificationContent.ID_REMOTE_PUSH,
                title = title,
                body = body,
                dedupeKey = dedupeKey,
                navRoute = navRoute,
            ),
        )
    }

    fun lastInvalidationStore(): LastInvalidationStore {
        check(started) { "UserNotificationCoordinator not started" }
        return lastInvalidationStore
    }

    private fun dispatchSessionInvalidation(event: SessionInvalidation) {
        if (event.appCode == ApiErrors.UNREACHABLE_APP_CODE) return
        // Application 级断隧道：后台被挤时也必须断开，不能只靠 MainActivity
        runCatching { onSessionInvalidated?.invoke() }
        val payload = UserNotificationContent.forSessionInvalidation(event)
        payload.killSwitchSubtitle?.let { AuthDisconnectReasonStore.set(appContext, it) }
        if (payload.persistForLoginBanner) {
            appScope.launch {
                lastInvalidationStore.save(
                    title = payload.loginBannerTitle ?: payload.title,
                    message = payload.body,
                    appCode = event.appCode,
                )
            }
        }
        postNotification(payload)
    }

    private fun dispatchForceDisconnect(reason: String) {
        runCatching { onSessionInvalidated?.invoke() }
        val payload = UserNotificationContent.forForceDisconnect(reason)
        payload.killSwitchSubtitle?.let { AuthDisconnectReasonStore.set(appContext, it) }
        postNotification(payload)
    }

    /** 数据面超时等导致的意外断开：仅通知，不登出。 */
    fun notifyVpnUnexpectedDisconnect(message: String) {
        if (!started) return
        postNotification(
            UserNotificationPayload(
                channelId = UserNotificationChannels.ACCOUNT_STATUS,
                notificationId = UserNotificationContent.ID_VPN_DROP,
                title = "VPN 已断开",
                body = message.ifBlank { "连接异常已断开，请返回 App 重连" },
                dedupeKey = "vpn_drop:${message.take(40)}",
                navRoute = UserNotificationContent.NAV_MAIN,
            ),
        )
    }

    private fun dispatchRechargeChange(change: RechargeStatusChange) {
        if (change.status != "paid" && change.status != "rejected") return
        postNotification(UserNotificationContent.forRechargeChange(change))
    }

    private fun postNotification(payload: UserNotificationPayload) {
        if (!shouldPost(payload.dedupeKey)) return
        if (!canPostNotifications()) return

        val intent =
            MainActivityIntents.openApp(appContext).apply {
                payload.navRoute?.let { putExtra(EXTRA_NAV_ROUTE, it) }
            }
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                payload.notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(appContext, payload.channelId)
                .setSmallIcon(R.drawable.ic_kuayun_cloud_small)
                .setContentTitle(payload.title)
                .setContentText(payload.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(
                    when (payload.channelId) {
                        UserNotificationChannels.ACCOUNT_SECURITY ->
                            NotificationCompat.PRIORITY_HIGH
                        UserNotificationChannels.APP_UPDATES ->
                            NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    },
                )
                .build()

        NotificationManagerCompat.from(appContext).notify(payload.notificationId, notification)
    }

    private fun shouldPost(dedupeKey: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(recentDedupeKeys) {
            val last = recentDedupeKeys[dedupeKey]
            if (last != null && now - last < DEDUPE_WINDOW_MS) {
                return false
            }
            recentDedupeKeys[dedupeKey] = now
            while (recentDedupeKeys.size > 32) {
                val oldest = recentDedupeKeys.entries.minByOrNull { it.value }?.key
                if (oldest != null) recentDedupeKeys.remove(oldest) else break
            }
        }
        return true
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channels =
            listOf(
                NotificationChannel(
                    UserNotificationChannels.ACCOUNT_SECURITY,
                    "账户与安全",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "登录失效、他端登录等安全提醒" },
                NotificationChannel(
                    UserNotificationChannels.ACCOUNT_STATUS,
                    "套餐与流量",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "套餐到期、流量用尽等状态提醒" },
                NotificationChannel(
                    UserNotificationChannels.ORDER_FINANCE,
                    "充值与订单",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "USDT 充值到账或驳回" },
                NotificationChannel(
                    UserNotificationChannels.APP_UPDATES,
                    "应用更新",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "新版本可选更新" },
                NotificationChannel(
                    UserNotificationChannels.REMOTE_PUSH,
                    "运营与公告",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "充值提醒、系统公告等远程推送" },
            )
        channels.forEach { manager.createNotificationChannel(it) }
    }
}
