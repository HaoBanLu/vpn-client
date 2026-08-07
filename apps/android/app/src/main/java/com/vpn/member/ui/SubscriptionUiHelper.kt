package com.vpn.member.ui

import com.vpn.member.data.api.PackageItem
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.data.api.SubscriptionUsage
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class SubscriptionStatusLabel(val text: String) {
    ACTIVE("使用中"),
    EXPIRING_SOON("即将到期"),
    LOW_TRAFFIC("流量不足"),
}

enum class PurchaseActionLabel(val text: String) {
    BUY("立即购买"),
    RENEW("续费"),
    UPGRADE("升级"),
    CHANGE("更换套餐"),
    CURRENT("当前套餐"),
    INSUFFICIENT_BALANCE("余额不足，去充值"),
}

data class PurchaseButtonState(
    val label: PurchaseActionLabel,
    val enabled: Boolean,
    val insufficientBalance: Boolean = false,
)

fun subscriptionPackageName(sub: SubscriptionActive?): String =
    sub?.`package`?.name ?: "当前套餐"

fun subscriptionStatusLabel(
    sub: SubscriptionActive?,
    usage: SubscriptionUsage?,
): SubscriptionStatusLabel? {
    if (sub == null) return null
    val remaining = usage?.remaining ?: (sub.traffic_total_gb - sub.traffic_used_gb)
    val total = usage?.total ?: sub.traffic_total_gb
    if (total > 0 && remaining <= total * 0.1) {
        return SubscriptionStatusLabel.LOW_TRAFFIC
    }
    val daysLeft = daysUntilExpiry(sub.expires_at)
    if (daysLeft != null && daysLeft in 0..7) {
        return SubscriptionStatusLabel.EXPIRING_SOON
    }
    return SubscriptionStatusLabel.ACTIVE
}

fun daysUntilExpiry(expiresAt: String?): Long? {
    if (expiresAt.isNullOrBlank()) return null
    return runCatching {
        val date = LocalDate.parse(expiresAt.take(10))
        ChronoUnit.DAYS.between(LocalDate.now(), date)
    }.getOrNull()
}

/** Gson 可能把缺失字段反序列化为 null，Compose 渲染前必须兜底。 */
fun formatExpiryDate(expiresAt: String?): String =
    expiresAt?.takeIf { it.isNotBlank() }?.take(10) ?: "—"

fun trafficProgress(usage: SubscriptionUsage?): Float {
    if (usage == null || usage.total <= 0) return 0f
    val raw = (usage.used / usage.total).toFloat()
    if (!raw.isFinite()) return 0f
    return raw.coerceIn(0f, 1f)
}

fun isCurrentPackage(sub: SubscriptionActive?, pkg: PackageItem): Boolean {
    if (sub == null) return false
    val subPackageId = sub.package_id ?: return false
    return subPackageId == pkg.id
}

fun purchaseButtonState(
    sub: SubscriptionActive?,
    pkg: PackageItem,
    userBalance: Double,
    paying: Boolean,
): PurchaseButtonState {
    if (paying) {
        return PurchaseButtonState(PurchaseActionLabel.BUY, enabled = false)
    }
    if (isCurrentPackage(sub, pkg)) {
        if (userBalance < pkg.price) {
            return PurchaseButtonState(PurchaseActionLabel.INSUFFICIENT_BALANCE, enabled = true, insufficientBalance = true)
        }
        return PurchaseButtonState(PurchaseActionLabel.RENEW, enabled = true)
    }
    if (userBalance < pkg.price) {
        return PurchaseButtonState(PurchaseActionLabel.INSUFFICIENT_BALANCE, enabled = true, insufficientBalance = true)
    }
    if (sub == null) {
        return PurchaseButtonState(PurchaseActionLabel.BUY, enabled = true)
    }
    val currentLevel = sub.`package`?.level ?: 1
    return when {
        pkg.level > currentLevel -> PurchaseButtonState(PurchaseActionLabel.UPGRADE, enabled = true)
        else -> PurchaseButtonState(PurchaseActionLabel.CHANGE, enabled = true)
    }
}

fun purchaseSuccessMessage(
    sub: SubscriptionActive?,
    pkg: PackageItem,
): String {
    if (sub == null) return "购买成功，请返回连接页"
    if (isCurrentPackage(sub, pkg)) return "续费成功，权益已延长"
    val currentLevel = sub.`package`?.level ?: 1
    return if (pkg.level > currentLevel) {
        "升级成功，请返回连接页"
    } else {
        "套餐已更换，请返回连接页"
    }
}

fun currentSubscriptionSummary(
    sub: SubscriptionActive,
    usage: SubscriptionUsage?,
): String {
    val remaining = usage?.let { "%.1f".format(it.remaining) } ?: "-"
    val expiry = formatExpiryDate(sub.expires_at)
    return "${subscriptionPackageName(sub)} · 剩余 ${remaining}GB · $expiry 到期"
}
