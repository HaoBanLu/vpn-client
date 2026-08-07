#!/usr/bin/env bash
# 从各端源码提取版本号，输出 release-meta.json（供 GitHub Release 正文）
# Android 与桌面统一读 apps/tauri APP_VERSION_*（apps/android 已存档）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-release-meta.json}"

TAURI_CONF="$ROOT/apps/tauri/src-tauri/tauri.conf.json"
TAURI_META="$ROOT/apps/tauri/src/lib/app-meta.ts"
IOS_PROJECT="$ROOT/apps/tauri/platforms/ios/project.yml"

TAURI_VERSION="$(grep -E '"version"' "$TAURI_CONF" | head -1 | sed -E 's/.*"version"\s*:\s*"([^"]+)".*/\1/')"
TAURI_CODE="$(grep 'APP_VERSION_CODE' "$TAURI_META" | head -1 | sed -E "s/.*APP_VERSION_CODE\s*=\s*([0-9]+).*/\1/")"
TAURI_NAME="$(grep 'APP_VERSION_NAME' "$TAURI_META" | head -1 | sed -E "s/.*APP_VERSION_NAME\s*=\s*'([^']+)'.*/\1/")"

IOS_NAME="$(grep 'MARKETING_VERSION:' "$IOS_PROJECT" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
IOS_CODE="$(grep 'CURRENT_PROJECT_VERSION:' "$IOS_PROJECT" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"

TAG="${GITHUB_REF_NAME:-local}"
SHA="${GITHUB_SHA:-unknown}"
UTC_NOW="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

cat >"$OUT" <<EOF
{
  "tag": "$TAG",
  "sha": "$SHA",
  "built_at_utc": "$UTC_NOW",
  "platforms": {
    "android": {
      "version_name": "$TAURI_NAME",
      "version_code": $TAURI_CODE,
      "source": "apps/tauri"
    },
    "windows": {
      "version_name": "$TAURI_VERSION",
      "version_code": $TAURI_CODE
    },
    "macos": {
      "version_name": "$TAURI_VERSION",
      "version_code": $TAURI_CODE
    },
    "ios": {
      "version_name": "$IOS_NAME",
      "version_code": "$IOS_CODE"
    }
  }
}
EOF

echo "Wrote $OUT"
cat "$OUT"
