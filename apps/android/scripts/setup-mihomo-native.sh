#!/usr/bin/env bash
# 从 ClashMetaForAndroid Release APK 提取 libbridge.so / libclash.so 到 mihomo-core/jniLibs。
# 用法：在 apps/android 目录执行 bash scripts/setup-mihomo-native.sh [版本号，默认 v2.11.30]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-v2.11.30}"
DL="$ROOT/tmp/cmfa-dl"
JNI="$ROOT/mihomo-core/src/main/jniLibs"

mkdir -p "$DL" "$JNI"

download_and_extract() {
  local abi="$1"
  local apk_name="$2"
  local url="https://github.com/MetaCubeX/ClashMetaForAndroid/releases/download/${VERSION}/${apk_name}"
  echo "==> $abi"
  curl -fsSL -o "$DL/$apk_name" "$url"
  mkdir -p "$JNI/$abi"
  unzip -oj "$DL/$apk_name" "lib/$abi/libbridge.so" "lib/$abi/libclash.so" -d "$JNI/$abi"
}

download_and_extract "arm64-v8a" "cmfa-${VERSION#v}-meta-arm64-v8a-release.apk"
download_and_extract "armeabi-v7a" "cmfa-${VERSION#v}-meta-armeabi-v7a-release.apk"
download_and_extract "x86_64" "cmfa-${VERSION#v}-meta-x86_64-release.apk"

bash "$(dirname "$0")/fetch-mihomo-geodata.sh"

echo "Done. Native libs installed under mihomo-core/src/main/jniLibs/"
