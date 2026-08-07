#!/usr/bin/env bash
# 弱网/断网稳定性快速验收（P2-5）：需 adb 连接真机或模拟器。
set -euo pipefail

PKG="com.vpn.member"
ADB="${ADB:-adb}"

echo "== VPN stability adb check =="
echo "package: $PKG"

echo "[1/4] 检查前台 VPN 服务"
$ADB shell dumpsys activity services "$PKG" | grep -i VpnTunnelService || echo "（服务未运行，若未连接属正常）"

echo "[2/4] 模拟飞行模式开"
$ADB shell cmd connectivity airplane-mode enable || true
sleep 3

echo "[3/4] 模拟飞行模式关"
$ADB shell cmd connectivity airplane-mode disable || true
sleep 5

echo "[4/4] 拉取最近 logcat（connect/reconnect/heal）"
$ADB logcat -d -t 80 | grep -E "ConnectViewModel|VpnTunnelService|MihomoTunnelRecovery|自动重连|reconnect" || true

echo "完成。请在 App 内确认：断网恢复后是否自动重连、状态提示是否明确。"
