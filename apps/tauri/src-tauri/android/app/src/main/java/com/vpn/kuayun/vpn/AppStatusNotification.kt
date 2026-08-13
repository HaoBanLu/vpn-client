package com.vpn.kuayun.vpn

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vpn.kuayun.MainActivity
import com.vpn.kuayun.R

/**
 * 常驻状态通知：打开 App 即显示未连接；连接中/已连接由 VpnTunnelService 接管同一 ID。
 * Android 13+ 必须先有 POST_NOTIFICATIONS，否则前台服务通知对用户不可见。
 */
object AppStatusNotification {
    const val CHANNEL_ID = "vpn_status"
    const val NOTIFICATION_ID = 1001
    const val PERMISSION_REQUEST = 2401

    fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPostPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasPostPermission(activity)) return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            PERMISSION_REQUEST,
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.deleteNotificationChannel("vpn_tunnel") }
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "跨云状态",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "显示跨云连接状态、节点与实时流量"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    fun showIdle(context: Context) {
        val state = VpnConnectionBus.status.value.state
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return
        notify(
            context,
            title = "跨云未连接",
            text = "点击打开应用，一键连接后即可加速",
            showDisconnect = false,
        )
    }

    fun showConnecting(context: Context, nodeName: String): Notification {
        val node = nodeName.trim().ifBlank { "智能选路" }
        return notify(
            context,
            title = "跨云连接中",
            text = "正在连接 $node",
            showDisconnect = true,
        )
    }

    fun showConnected(
        context: Context,
        nodeName: String,
        stats: VpnSessionStats,
        rates: VpnTrafficRates,
    ): Notification {
        val node = nodeName.trim().ifBlank { "智能选路" }
        val total = stats.uploadBytes + stats.downloadBytes
        val text =
            buildString {
                append(node)
                append(" · ↑ ")
                append(VpnSessionStatsTracker.formatSpeed(rates.uploadBps))
                append(" ↓ ")
                append(VpnSessionStatsTracker.formatSpeed(rates.downloadBps))
                append(" · ")
                append(VpnSessionStatsTracker.formatDuration(stats.durationMs))
                append(" · 累计 ")
                append(VpnSessionStatsTracker.formatBytes(total))
            }
        return notify(
            context,
            title = "跨云已连接",
            text = text,
            showDisconnect = true,
        )
    }

    fun notify(
        context: Context,
        title: String,
        text: String,
        showDisconnect: Boolean,
    ): Notification {
        val notification = build(context, title, text, showDisconnect)
        if (hasPostPermission(context)) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        }
        return notification
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        showDisconnect: Boolean,
    ): Notification {
        ensureChannel(context)
        val openIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_kuayun_cloud_small)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (showDisconnect) {
            val disconnectIntent =
                PendingIntent.getService(
                    context,
                    1,
                    Intent(context, VpnTunnelService::class.java).apply {
                        action = VpnTunnelService.ACTION_DISCONNECT
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(0, "断开", disconnectIntent)
        }
        return builder.build()
    }
}
