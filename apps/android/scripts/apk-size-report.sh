#!/usr/bin/env bash
# APK 体积分解（需已构建 Release APK）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/release/app-arm64-v8a-release.apk}"

if [[ ! -f "$APK" ]]; then
  echo "APK 不存在: $APK"
  echo "请先执行: ./gradlew :app:assembleRelease -PreleaseArm64Only=true"
  exit 1
fi

echo "== APK 体积报告 =="
ls -lh "$APK"
echo ""
echo "Top 15 (uncompressed bytes):"
unzip -l "$APK" | awk 'NR>3 && NF>=4 {print}' | sort -k1 -nr | head -15
echo ""
SO_TOTAL=$(unzip -l "$APK" | awk '/\.so$/ {s+=$1} END {print s+0}')
DEX_TOTAL=$(unzip -l "$APK" | awk '/classes.*\.dex$/ {s+=$1} END {print s+0}')
echo "Native .so 合计: $(echo "scale=2; $SO_TOTAL/1024/1024" | bc) MB"
echo "DEX 合计: $(echo "scale=2; $DEX_TOTAL/1024/1024" | bc) MB"
