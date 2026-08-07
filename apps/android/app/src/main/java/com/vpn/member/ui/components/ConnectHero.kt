package com.vpn.member.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpn.member.ui.displayNodeName
import com.vpn.member.ui.theme.ConnectVisual
import com.vpn.member.vpn.ConnectionState

data class ConnectHeroCopy(
    val title: String,
    val subtitle: String,
    val buttonLabel: String,
    val buttonBrush: Brush,
    val titleColor: Color,
    val glowColor: Color,
    val connected: Boolean,
    val connecting: Boolean,
)

@Composable
fun ConnectHero(
    copy: ConnectHeroCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ConnectHeroButton(
            label = copy.buttonLabel,
            buttonBrush = copy.buttonBrush,
            glowColor = copy.glowColor,
            connected = copy.connected,
            connecting = copy.connecting,
            onClick = onClick,
        )
        Text(
            text = copy.title,
            style =
                if (copy.connected) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
            fontWeight = FontWeight.SemiBold,
            color = copy.titleColor,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
        )
        // 已连接时节点名由详情卡展示，避免与 Hero 副标题重复占高
        if (!copy.connected && copy.subtitle.isNotBlank()) {
            Text(
                text = copy.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ConnectVisual.subtitleMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ConnectHeroButton(
    label: String,
    buttonBrush: Brush,
    glowColor: Color,
    connected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val pressRipple = rememberRipple(color = Color.White.copy(alpha = 0.35f), bounded = false, radius = 64.dp)

    val pressScale by animateFloatAsState(
        targetValue =
            when {
                !isPressed || connecting -> 1f
                connected -> 0.96f
                else -> 0.93f
            },
        animationSpec =
            if (connected) {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh,
                )
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            },
        label = "hero-press-scale",
    )
    val outerScale = pressScale

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            connecting ->
                ExpandingRippleRings(
                    color = glowColor,
                    ringCount = 3,
                    cycleMs = 2400,
                    maxAlpha = 0.42f,
                    expandRangeDp = 42f,
                )
            connected -> ConnectedSteadyShield(color = glowColor)
        }

        Box(
            modifier =
                Modifier
                    .size(156.dp)
                    .graphicsLayer {
                        scaleX = outerScale
                        scaleY = outerScale
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    glowColor.copy(
                                        alpha =
                                            when {
                                                connected -> 0.18f
                                                connecting -> 0.14f
                                                else -> 0.10f
                                            },
                                    ),
                                    Color.Transparent,
                                ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(124.dp)
                        .shadow(
                            elevation = if (isPressed) 6.dp else if (connected) 16.dp else 10.dp,
                            shape = CircleShape,
                            ambientColor = glowColor.copy(alpha = if (connected) 0.42f else 0.35f),
                            spotColor = glowColor.copy(alpha = if (connected) 0.52f else 0.45f),
                        )
                        .clip(CircleShape)
                        .background(buttonBrush)
                        .then(
                            if (connected) {
                                Modifier.border(2.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = pressRipple,
                            // 连接中仍允许点击，由外层 onClick 中断 VpnTunnelService
                            enabled = true,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClick()
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (connected) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.White.copy(alpha = 0.14f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.06f),
                                            ),
                                    ),
                                ),
                    )
                }
                ConnectButtonIconLayer(
                    label = label,
                    connected = connected,
                )
            }
        }
    }
}

/** 连接过程：向外扩散波纹（仅在 connecting 时播放）。 */
@Composable
private fun ExpandingRippleRings(
    color: Color,
    ringCount: Int,
    cycleMs: Int,
    maxAlpha: Float = 0.35f,
    expandRangeDp: Float = 36f,
) {
    val transition = rememberInfiniteTransition(label = "ripple-rings")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = cycleMs, easing = LinearEasing),
            ),
        label = "ripple-progress",
    )

    Canvas(modifier = Modifier.size(194.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = 64.dp.toPx()
        val expandRange = expandRangeDp.dp.toPx()
        repeat(ringCount) { index ->
            val phase = (progress + index.toFloat() / ringCount) % 1f
            val radius = baseRadius + phase * expandRange
            val alpha = (1f - phase).coerceIn(0f, 1f) * maxAlpha
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** 已连接：固定多层护盾环 + 极慢亮度微动，传达「稳态保护」而非呼吸起伏。 */
@Composable
private fun ConnectedSteadyShield(color: Color) {
    val transition = rememberInfiniteTransition(label = "steady-shield")
    val outerShimmer by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "steady-shield-shimmer",
    )

    Canvas(modifier = Modifier.size(204.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.10f), Color.Transparent),
                    center = center,
                    radius = 82.dp.toPx(),
                ),
            radius = 82.dp.toPx(),
            center = center,
        )
        val rings =
            listOf(
                Triple(62.dp.toPx(), 2.dp.toPx(), 0.24f),
                Triple(70.dp.toPx(), 1.8.dp.toPx(), 0.17f),
                Triple(78.dp.toPx(), 1.5.dp.toPx(), 0.11f * outerShimmer),
            )
        rings.forEach { (radius, stroke, alpha) ->
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun ConnectButtonIconLayer(
    label: String,
    connected: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ConnectButtonIconContent(label = label, connected = connected)
    }
}

@Composable
private fun ConnectButtonIconContent(
    label: String,
    connected: Boolean,
) {
    Icon(
        imageVector = if (connected) Icons.Filled.Shield else Icons.Filled.PowerSettingsNew,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(if (connected) 30.dp else 28.dp),
    )
    Text(
        text = label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = if (connected) FontWeight.SemiBold else FontWeight.Medium,
        letterSpacing = if (connected) 0.5.sp else 0.3.sp,
    )
}

fun resolveConnectHeroCopy(
    connectionState: ConnectionState,
    connectPending: Boolean,
    isSwitching: Boolean,
    connectedNodeName: String?,
    selectedNode: String?,
    tunnelLatencyMs: Int? = null,
    entryLatencyMs: Int? = null,
): ConnectHeroCopy {
    val nodeLabel =
        displayNodeName(selectedNode ?: connectedNodeName).ifBlank { "未选择节点" }
    val latencyHint = formatLatencyHint(tunnelLatencyMs, entryLatencyMs)
    val connecting =
        connectPending ||
            connectionState == ConnectionState.CONNECTING ||
            isSwitching
    val connected = connectionState == ConnectionState.CONNECTED

    return when {
        connecting ->
            ConnectHeroCopy(
                title = if (isSwitching) "切换中" else "连接中",
                subtitle =
                    buildString {
                        if (!selectedNode.isNullOrBlank()) {
                            append(if (isSwitching) "正在切换至 " else "正在连接 ")
                            append(nodeLabel)
                        } else if (connectPending && connectionState != ConnectionState.CONNECTING) {
                            append("正在准备连接…")
                        } else {
                            append("正在建立加密隧道…")
                        }
                        if (latencyHint.isNotBlank()) {
                            append(" · ")
                            append(latencyHint)
                        }
                    },
                buttonLabel =
                    when {
                        isSwitching -> "切换中"
                        connectPending && connectionState != ConnectionState.CONNECTING -> "准备中"
                        else -> "连接中"
                    },
                buttonBrush = ConnectVisual.connectingButtonBrush(),
                titleColor = ConnectVisual.connectingBlue,
                glowColor = ConnectVisual.connectingBlue,
                connected = false,
                connecting = true,
            )
        connected ->
            ConnectHeroCopy(
                title = "已保护",
                subtitle = nodeLabel,
                buttonLabel = "断开",
                buttonBrush = ConnectVisual.protectedButtonBrush(),
                titleColor = ConnectVisual.protectedGreen,
                glowColor = ConnectVisual.protectedGreen,
                connected = true,
                connecting = false,
            )
        connectionState == ConnectionState.FAILED ->
            ConnectHeroCopy(
                title = "连接失败",
                subtitle = "请检查网络或切换节点",
                buttonLabel = "一键连接",
                buttonBrush = ConnectVisual.connectButtonBrush(),
                titleColor = ConnectVisual.errorRed,
                glowColor = ConnectVisual.brandBlue,
                connected = false,
                connecting = false,
            )
        else ->
            ConnectHeroCopy(
                title = "未连接",
                subtitle =
                    if (selectedNode != null) {
                        "已选 $nodeLabel · 点击下方连接"
                    } else {
                        "点击「一键连接」前往选择节点"
                    },
                buttonLabel = "一键连接",
                buttonBrush = ConnectVisual.connectButtonBrush(),
                titleColor = ConnectVisual.titlePrimary,
                glowColor = ConnectVisual.brandBlue,
                connected = false,
                connecting = false,
            )
    }
}

private fun formatLatencyHint(tunnelLatencyMs: Int?, entryLatencyMs: Int?): String {
    val parts = mutableListOf<String>()
    entryLatencyMs?.takeIf { it > 0 }?.let { parts.add("入口 ${it}ms") }
    tunnelLatencyMs?.takeIf { it > 0 }?.let { parts.add("隧道 ${it}ms") }
    return parts.joinToString(" · ")
}
