#!/usr/bin/env bash
# 将 Gradle CLI 产物复制到 app/release/（Android Studio 构建已直接输出到该目录，无需运行）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/build/outputs/apk/release"
DST="$ROOT/app/release"

if [[ ! -d "$SRC" ]]; then
  echo "源目录不存在，请先执行: ./gradlew :app:assembleRelease"
  exit 1
fi

mkdir -p "$DST"
cp -f "$SRC"/app-*-release.apk "$DST"/
[[ -f "$SRC/output-metadata.json" ]] && cp -f "$SRC/output-metadata.json" "$DST"/
if [[ -d "$SRC/baselineProfiles" ]]; then
  rm -rf "$DST/baselineProfiles"
  cp -a "$SRC/baselineProfiles" "$DST"/
fi

echo "已复制到 $DST"
ls -lh "$DST"/app-*-release.apk 2>/dev/null || true
