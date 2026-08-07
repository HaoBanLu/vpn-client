#!/usr/bin/env bash
# 校验 Release APK 证书是否与基准包一致（app/release/app-arm64-v8a-release.apk）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REF_APK="$ROOT/app/release/app-arm64-v8a-release.apk"
TARGET_APK="${1:-$ROOT/app/build/outputs/apk/release/app-arm64-v8a-release.apk}"
REF_SHA256="b493d999430264980a30c9f8194aa3a31d2a4ff281c69806890065ed15be6779"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-D:/Android/Sdk}}"
APKSIGNER="$(find "$SDK/build-tools" -name apksigner.bat -o -name apksigner 2>/dev/null | sort -V | tail -1)"

if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" && ! -f "$APKSIGNER" ]]; then
  echo "未找到 apksigner，请设置 ANDROID_HOME"
  exit 1
fi

if [[ ! -f "$TARGET_APK" ]]; then
  echo "待校验 APK 不存在: $TARGET_APK"
  exit 1
fi

extract_sha256() {
  "$APKSIGNER" verify --print-certs "$1" 2>/dev/null |
    rg -i "SHA-256 digest:" |
    head -1 |
    sed -E 's/.*SHA-256 digest: //' |
    tr -d ':\r\n' |
    tr 'A-F' 'a-f'
}

TARGET_SHA="$(extract_sha256 "$TARGET_APK")"
echo "目标 APK: $TARGET_APK"
echo "SHA-256 : $TARGET_SHA"

if [[ "$TARGET_SHA" == "$REF_SHA256" ]]; then
  echo "OK: 与正式 Release 签名一致 (CN=lu, alias=key0)"
  exit 0
fi

if [[ -f "$REF_APK" ]]; then
  REF_SHA="$(extract_sha256 "$REF_APK")"
  echo "基准 APK: $REF_APK"
  echo "基准 SHA : $REF_SHA"
  if [[ "$TARGET_SHA" == "$REF_SHA" ]]; then
    echo "OK: 与 app/release 基准包签名一致"
    exit 0
  fi
fi

echo "FAIL: 签名与正式 Release 不一致"
exit 1
