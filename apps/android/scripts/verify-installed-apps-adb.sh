#!/usr/bin/env bash
# 真机 USB 验证：对比「全部已安装包」与「桌面可启动应用」，并检查分身大师 com.qihoo.magic
set -euo pipefail

CLONE_MASTER="com.qihoo.magic"

total="$(adb shell pm list packages 2>/dev/null | wc -l | tr -d ' ')"
launcher="$(adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>/dev/null | grep -o 'packageName=[^ ]*' | sort -u | wc -l | tr -d ' ')"
has_magic="$(adb shell pm list packages "$CLONE_MASTER" 2>/dev/null | grep -c "$CLONE_MASTER" || true)"
magic_in_launcher="$(adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$CLONE_MASTER" 2>/dev/null | grep -c 'packageName=' || true)"
magic_default="$(adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$CLONE_MASTER" 2>/dev/null | grep -o 'isDefault=[^ ]*' | head -1 || true)"

echo "=== 应用直连枚举对比（adb）==="
echo "全部已安装包 (pm list packages):        $total"
echo "桌面可启动应用 (query LAUNCHER, 去重):  $launcher"
echo "分身大师 $CLONE_MASTER 已安装:          $([ "$has_magic" -gt 0 ] && echo yes || echo no)"
echo "分身大师 LAUNCHER Activity 数:           $magic_in_launcher"
echo "分身大师 Launcher isDefault:            ${magic_default:-unknown}"
echo ""
echo "LibChecker 使用 getInstalledApplications + QUERY_ALL_PACKAGES + GET_INSTALLED_APPS"
echo "跨云新逻辑对齐上述方式；旧逻辑仅 LAUNCHER 约 $launcher 个，且 MATCH_DEFAULT_ONLY 可能漏掉 isDefault=false 的应用。"
