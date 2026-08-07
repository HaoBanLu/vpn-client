#!/usr/bin/env bash
# 发版前 E3 提示：请在管理后台「注册设置 → 崩溃率发版门禁」核对 PASS/REVIEW/BLOCK。
set -euo pipefail

echo "=========================================="
echo "E3 崩溃率发版门禁（人工核对）"
echo "=========================================="
echo "1. 打开管理后台 → 设置 → 注册设置 → 卡片「崩溃率发版门禁（近 7 日）」"
echo "2. 确认 release_gate_recommendation："
echo "   - PASS：可发版"
echo "   - REVIEW：样本不足或有 crash 记录，需人工判断"
echo "   - BLOCK：崩溃率 > 0.5%，建议修复后再发版"
echo "3. API：GET /api/v1/admin/users/app-debug/crash-health?days=7"
echo ""
echo "若 BLOCK 仍须紧急发版，请在发版记录中注明原因与回滚方案。"
