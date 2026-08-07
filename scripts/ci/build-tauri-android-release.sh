#!/usr/bin/env bash
# CI / Linux：构建 apps/tauri Android arm64 Release APK 并签名。
# 依赖：Node、Rust、Android SDK/NDK、JDK 17；密钥经 ANDROID_* Secrets 或仓库内 keystore。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TAURI="$ROOT/apps/tauri"
ANDROID_ARCHIVE="$ROOT/apps/android"
OUT_DIR="$ROOT/apps"
GEN_APK_DIR="$TAURI/src-tauri/gen/android/app/build/outputs/apk"

cd "$TAURI"

VERSION_NAME="$(grep -E "APP_VERSION_NAME" src/lib/app-meta.ts | head -1 | sed -E "s/.*APP_VERSION_NAME\\s*=\\s*'([^']+)'.*/\\1/")"
VERSION_CODE="$(grep -E "APP_VERSION_CODE" src/lib/app-meta.ts | head -1 | sed -E "s/.*APP_VERSION_CODE\\s*=\\s*([0-9]+).*/\\1/")"
if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
  echo "ERROR: failed to read APP_VERSION_* from src/lib/app-meta.ts" >&2
  exit 1
fi
echo "Building Tauri Android $VERSION_NAME ($VERSION_CODE)"

# 存档工程内 mihomo-core 仍提供 JNI（见 apps/android/ARCHIVE.md）
if [[ ! -f "$ANDROID_ARCHIVE/mihomo-core/src/main/jniLibs/arm64-v8a/libclash.so" ]]; then
  echo "Fetching mihomo native libs into archived apps/android/mihomo-core ..."
  bash "$ANDROID_ARCHIVE/scripts/setup-mihomo-native.sh"
fi

if [[ ! -d src-tauri/gen/android/app ]]; then
  echo "Initializing Tauri Android project..."
  npx tauri android init --ci
fi

bash scripts/sync-android-vpn.sh

bash "$ROOT/scripts/ci/setup-android-signing.sh" "$ANDROID_ARCHIVE"

echo "Running tauri android build --target aarch64 ..."
npx tauri android build --target aarch64

# 优先 unsigned，避免二次签名冲突
APK=""
if APK="$(find "$GEN_APK_DIR" -type f -name '*arm64*release*unsigned*.apk' | sort | tail -1)" && [[ -n "$APK" ]]; then
  :
elif APK="$(find "$GEN_APK_DIR" -type f -name '*arm64*release*.apk' | sort | tail -1)" && [[ -n "$APK" ]]; then
  :
else
  echo "ERROR: release APK not found under $GEN_APK_DIR" >&2
  find "$GEN_APK_DIR" -type f -name '*.apk' 2>/dev/null || true
  exit 1
fi
echo "Built: $APK"

SIGNED_APK="$OUT_DIR/kuayun-android-${VERSION_NAME}-${VERSION_CODE}-arm64.apk"
mkdir -p "$OUT_DIR"

KEYSTORE="$ANDROID_ARCHIVE/keystore/kuayun-release.keystore"
PROPS="$ANDROID_ARCHIVE/keystore.properties"
if [[ ! -f "$KEYSTORE" || ! -f "$PROPS" ]]; then
  echo "ERROR: release keystore missing after setup-android-signing" >&2
  exit 1
fi

store_pass="$(grep -E '^storePassword=' "$PROPS" | head -1 | cut -d= -f2-)"
key_alias="$(grep -E '^keyAlias=' "$PROPS" | head -1 | cut -d= -f2-)"
key_pass="$(grep -E '^keyPassword=' "$PROPS" | head -1 | cut -d= -f2-)"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" ]]; then
  echo "ERROR: ANDROID_HOME / ANDROID_SDK_ROOT not set" >&2
  exit 1
fi
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner | sort -V | tail -1 || true)"
if [[ -z "$APKSIGNER" ]]; then
  echo "ERROR: apksigner not found under $SDK_ROOT/build-tools" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp -f "$APK" "$WORK/in.apk"

# 若已有签名，剥掉 META-INF 再签
if unzip -l "$WORK/in.apk" | grep -q 'META-INF/.*\.SF'; then
  echo "Stripping existing signature..."
  mkdir -p "$WORK/unpack"
  unzip -q "$WORK/in.apk" -d "$WORK/unpack"
  rm -rf "$WORK/unpack/META-INF"
  (cd "$WORK/unpack" && zip -qr "$WORK/stripped.apk" .)
  mv "$WORK/stripped.apk" "$WORK/in.apk"
fi

ZIPALIGN="$(dirname "$APKSIGNER")/zipalign"
if [[ -x "$ZIPALIGN" ]]; then
  "$ZIPALIGN" -f -p 4 "$WORK/in.apk" "$WORK/aligned.apk"
else
  cp -f "$WORK/in.apk" "$WORK/aligned.apk"
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$key_alias" \
  --ks-pass "pass:${store_pass}" \
  --key-pass "pass:${key_pass}" \
  --out "$SIGNED_APK" \
  "$WORK/aligned.apk"
"$APKSIGNER" verify --verbose "$SIGNED_APK"

SIZE="$(stat -c%s "$SIGNED_APK" 2>/dev/null || stat -f%z "$SIGNED_APK")"
echo "Signed APK: $SIGNED_APK ($SIZE bytes)"
if [[ "$SIZE" -lt 20000000 ]]; then
  echo "ERROR: APK too small (<20MB); native libs likely missing" >&2
  exit 1
fi

unzip -l "$SIGNED_APK" | grep -q 'lib/arm64-v8a/libclash.so' || {
  echo "ERROR: libclash.so not found in APK" >&2
  exit 1
}
unzip -l "$SIGNED_APK" | grep -q 'lib/arm64-v8a/libbridge.so' || {
  echo "ERROR: libbridge.so not found in APK" >&2
  exit 1
}

echo "OK: $SIGNED_APK"
