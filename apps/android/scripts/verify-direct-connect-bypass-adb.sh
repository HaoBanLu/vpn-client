#!/usr/bin/env bash
# 验证应用直连：分身大师 com.qihoo.magic 是否写入 VPN 排除列表（需已连接跨云 VPN）
set -euo pipefail

PKG_VPN="com.vpn.member"
PKG_MAGIC="com.qihoo.magic"
PREFS_XML='<?xml version='"'"'1.0'"'"' encoding='"'"'utf-8'"'"' standalone='"'"'yes'"'"' ?>
<map>
    <boolean name="privacy_accepted" value="true" />
    <set name="direct_connect_packages">
        <string>com.qihoo.magic</string>
    </set>
</map>'

echo "=== 应用直连 bypass 验证 ==="
echo ""

if ! adb shell pm list packages "$PKG_VPN" | grep -q "$PKG_VPN"; then
  echo "❌ 未安装 $PKG_VPN，请先安装 APK"
  exit 1
fi

if ! adb shell pm list packages "$PKG_MAGIC" | grep -q "$PKG_MAGIC"; then
  echo "❌ 未安装分身大师 $PKG_MAGIC"
  exit 1
fi

echo "[1/4] 写入 direct_connect_packages（含 $PKG_MAGIC）…"
adb shell "run-as $PKG_VPN sh -c 'cat > shared_prefs/vpn_member_prefs.xml'" <<<"$PREFS_XML"
echo "     prefs 已写入"

echo "[2/4] 读取确认…"
adb shell "run-as $PKG_VPN cat shared_prefs/vpn_member_prefs.xml" | grep -E "direct_connect|qihoo" || true

echo "[3/4] 清空 logcat，请在手机上：打开跨云 → 连接 VPN（或切换直连后自动重连）"
adb logcat -c
echo "     等待 45 秒采集 VpnDiag…"
sleep 45

echo "[4/4] 检查 VpnDiag direct_connect 日志…"
LOG="$(adb logcat -d -s VpnDiag:I 2>/dev/null | grep direct_connect || true)"
if echo "$LOG" | grep -q "com.qihoo.magic"; then
  echo "✅ 已确认：TUN 建立时 addDisallowedApplication 包含 com.qihoo.magic"
  echo "$LOG" | tail -3
else
  echo "⚠️  未抓到 direct_connect 日志（可能 VPN 未连接）。请连接后执行："
  echo "    adb logcat -d -s VpnDiag:I | grep direct_connect"
fi

MAGIC_UID="$(adb shell dumpsys package "$PKG_MAGIC" 2>/dev/null | grep -m1 'userId=' | sed 's/.*userId=//' | tr -d ' ' || true)"
echo ""
echo "分身大师 UID: ${MAGIC_UID:-unknown}"
echo "机制：Android addDisallowedApplication → 该包名进程不走 TUN，等同未开 VPN"
echo "注意：分身大师「内部」多开的微信/QQ 若系统仍识别为 com.tencent.mm，需单独对微信开直连"
