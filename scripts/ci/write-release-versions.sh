#!/usr/bin/env bash
# 从各端源码提取版本号，输出 release-meta.json（供 GitHub Release 正文）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-release-meta.json}"

ANDROID_GRADLE="$ROOT/apps/android/app/build.gradle.kts"
TAURI_CONF="$ROOT/apps/tauri/src-tauri/tauri.conf.json"
TAURI_META="$ROOT/apps/tauri/src/lib/app-meta.ts"
IOS_PROJECT="$ROOT/apps/tauri/platforms/ios/project.yml"

read_gradle() {
  local key="$1"
  grep -E "^\s*${key}\s*=" "$ANDROID_GRADLE" | head -1 | sed -E 's/.*=\s*"([^"]+)".*/\1/' || true
}

read_gradle_int() {
  local key="$1"
  grep -E "^\s*${key}\s*=" "$ANDROID_GRADLE" | head -1 | sed -E 's/.*=\s*([0-9]+).*/\1/' || true
}

ANDROID_NAME="$(read_gradle versionName)"
ANDROID_CODE="$(read_gradle_int versionCode)"

TAURI_VERSION="$(grep -E '"version"' "$TAURI_CONF" | head -1 | sed -E 's/.*"version"\s*:\s*"([^"]+)".*/\1/')"
TAURI_CODE="$(grep 'APP_VERSION_CODE' "$TAURI_META" | head -1 | sed -E "s/.*APP_VERSION_CODE\s*=\s*([0-9]+).*/\1/")"

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
      "version_name": "$ANDROID_NAME",
      "version_code": $ANDROID_CODE
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
