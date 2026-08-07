package com.vpn.member.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 连接页专用色板，与 [KuayunTheme] 品牌蓝保持一致。 */
object ConnectVisual {
    val brandBlue = Color(0xFF1B4DFF)
    val brandBlueLight = Color(0xFF4F7CFF)
    val titlePrimary = Color(0xFF0F1729)
    val subtitleMuted = Color(0xFF64748B)
    val protectedGreen = Color(0xFF16A34A)
    val protectedGreenLight = Color(0xFF22C55E)
    /** 在线 / 低延迟等正向状态，与连接页「已保护」绿系一致 */
    val onlineGreen = Color(0xFF4CAF50)
    val activeRowBackground = Color(0xFFE8F5E9)
    val fastestGreen = Color(0xFF2E7D32)
    val connectingBlue = Color(0xFF2563EB)
    val errorRed = Color(0xFFDC2626)
    val errorRedLight = Color(0xFFEF4444)
    val degradedAmber = Color(0xFFD97706)
    val degradedAmberLight = Color(0xFFF59E0B)

    fun connectButtonBrush(): Brush =
        Brush.linearGradient(
            colors = listOf(brandBlue, brandBlueLight),
        )

    fun protectedButtonBrush(): Brush =
        Brush.linearGradient(
            colors = listOf(protectedGreen, protectedGreenLight),
        )

    fun connectingButtonBrush(): Brush =
        Brush.linearGradient(
            colors = listOf(connectingBlue, brandBlueLight),
        )

    fun errorButtonBrush(): Brush =
        Brush.linearGradient(
            colors = listOf(errorRed, errorRedLight),
        )

    fun degradedButtonBrush(): Brush =
        Brush.linearGradient(
            colors = listOf(degradedAmber, degradedAmberLight),
        )
}
