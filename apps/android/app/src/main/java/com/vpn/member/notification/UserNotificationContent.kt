package com.vpn.member.notification

import com.vpn.member.data.session.RechargeStatusChange
import com.vpn.member.data.session.SessionInvalidation

/** 用户可见通知载荷（文案与渠道映射，便于单测）。 */
data class UserNotificationPayload(
    val channelId: String,
    val notificationId: Int,
    val title: String,
    val body: String,
    val dedupeKey: String,
    val navRoute: String? = null,
    val killSwitchSubtitle: String? = null,
    val persistForLoginBanner: Boolean = false,
    val loginBannerTitle: String? = null,
)

object UserNotificationChannels {
    const val ACCOUNT_SECURITY = "account_security"
    const val ACCOUNT_STATUS = "account_status"
    const val ORDER_FINANCE = "order_finance"
    const val APP_UPDATES = "app_updates"
    const val REMOTE_PUSH = "remote_push"
}

object UserNotificationContent {
    const val ID_REMOTE_PUSH = 20_005
    const val ID_VPN_DROP = 20_006

    private const val ID_ACCOUNT_SECURITY = 20_001
    private const val ID_ACCOUNT_STATUS = 20_002
    private const val ID_ORDER_FINANCE = 20_003
    private const val ID_APP_UPDATES = 20_004

    fun forSessionInvalidation(event: SessionInvalidation): UserNotificationPayload {
        val appCode = event.appCode?.trim().orEmpty()
        return when (appCode) {
            "LOGIN_ON_ANOTHER_DEVICE" ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ACCOUNT_SECURITY,
                    notificationId = ID_ACCOUNT_SECURITY,
                    title = "账号在其他设备登录",
                    body = event.message.ifBlank { "本机 VPN 已断开，请重新登录" },
                    dedupeKey = "auth:$appCode",
                    navRoute = NAV_LOGIN,
                    killSwitchSubtitle = "因账号在其他设备登录，网络已暂停",
                    persistForLoginBanner = true,
                    loginBannerTitle = "账号在其他设备登录",
                )
            "SESSION_REVOKED" ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ACCOUNT_SECURITY,
                    notificationId = ID_ACCOUNT_SECURITY,
                    title = "登录状态已失效",
                    body = event.message.ifBlank { "请重新登录后再次连接" },
                    dedupeKey = "auth:$appCode",
                    navRoute = NAV_LOGIN,
                    killSwitchSubtitle = "因登录状态已失效，网络已暂停",
                    persistForLoginBanner = true,
                    loginBannerTitle = "登录状态已失效",
                )
            else ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ACCOUNT_SECURITY,
                    notificationId = ID_ACCOUNT_SECURITY,
                    title = "登录已过期",
                    body = event.message.ifBlank { "为保护隐私，已断开 VPN。请重新登录" },
                    dedupeKey = "auth:token_expired",
                    navRoute = NAV_LOGIN,
                    killSwitchSubtitle = "因登录已过期，网络已暂停",
                    persistForLoginBanner = true,
                    loginBannerTitle = "登录已过期",
                )
        }
    }

    fun forForceDisconnect(reason: String): UserNotificationPayload {
        val normalized = reason.trim().lowercase()
        return when (normalized) {
            "subscription_expired" ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ACCOUNT_STATUS,
                    notificationId = ID_ACCOUNT_STATUS,
                    title = "套餐已到期",
                    body = "VPN 已断开，请续费后重新连接",
                    dedupeKey = "status:subscription_expired",
                    navRoute = NAV_MAIN,
                    killSwitchSubtitle = "因套餐已到期，网络已暂停",
                )
            "traffic_exceeded" ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ACCOUNT_STATUS,
                    notificationId = ID_ACCOUNT_STATUS,
                    title = "流量已用尽",
                    body = "VPN 已断开，请升级套餐或等待重置",
                    dedupeKey = "status:traffic_exceeded",
                    navRoute = NAV_MAIN,
                    killSwitchSubtitle = "因流量已用尽，网络已暂停",
                )
            else ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ACCOUNT_STATUS,
                    notificationId = ID_ACCOUNT_STATUS,
                    title = "订阅状态变更",
                    body = "VPN 已断开，请检查套餐与流量",
                    dedupeKey = "status:$normalized",
                    navRoute = NAV_MAIN,
                    killSwitchSubtitle = "因订阅状态变更，网络已暂停",
                )
        }
    }

    fun forRechargeChange(change: RechargeStatusChange): UserNotificationPayload {
        return when (change.status) {
            "paid" ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ORDER_FINANCE,
                    notificationId = ID_ORDER_FINANCE + (change.orderId % 100).toInt(),
                    title = "USDT 充值已到账",
                    body = "订单 ${change.orderNo} 已确认，余额已更新",
                    dedupeKey = "recharge:${change.orderId}:paid",
                    navRoute = NAV_RECHARGE_ORDERS,
                )
            "rejected" ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ORDER_FINANCE,
                    notificationId = ID_ORDER_FINANCE + (change.orderId % 100).toInt(),
                    title = "USDT 充值被驳回",
                    body = "订单 ${change.orderNo} 请查看驳回原因",
                    dedupeKey = "recharge:${change.orderId}:rejected",
                    navRoute = NAV_RECHARGE_ORDERS,
                )
            else ->
                UserNotificationPayload(
                    channelId = UserNotificationChannels.ORDER_FINANCE,
                    notificationId = ID_ORDER_FINANCE,
                    title = "充值订单状态已更新",
                    body = "订单 ${change.orderNo} 状态已变更",
                    dedupeKey = "recharge:${change.orderId}:${change.status}",
                    navRoute = NAV_RECHARGE_ORDERS,
                )
        }
    }

    const val NAV_LOGIN = "login"
    const val NAV_MAIN = "main"
    const val NAV_RECHARGE_ORDERS = "recharge_orders"
}
