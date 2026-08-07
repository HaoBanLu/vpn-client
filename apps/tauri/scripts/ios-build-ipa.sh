#!/usr/bin/env bash
# iPhone 真机 IPA：xcframework + archive + exportArchive（需 macOS + Apple 签名）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/platforms/ios"
BUILD_DIR="$IOS/build"
ARCHIVE_PATH="$BUILD_DIR/KuayunVPN.xcarchive"
EXPORT_DIR="$BUILD_DIR/export"
EXPORT_PLIST="$BUILD_DIR/ExportOptions.plist"
TEMPLATE_PLIST="$ROOT/scripts/ios-export-options.plist"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "ERROR: iOS IPA build requires macOS + Xcode." >&2
  exit 1
fi

: "${APPLE_TEAM_ID:?APPLE_TEAM_ID is required for IPA export}"
IOS_EXPORT_METHOD="${IOS_EXPORT_METHOD:-app-store}"

echo "==> iOS IPA build (method=$IOS_EXPORT_METHOD, team=$APPLE_TEAM_ID)"

bash "$ROOT/scripts/fetch-mihomo-ios.sh"

if [[ -d "$IOS/vendor/Mihomo.xcframework" ]]; then
  echo "Using cached Mihomo.xcframework"
else
  echo "Building Mihomo.xcframework (may take several minutes) ..."
  bash "$ROOT/scripts/build-mihomo-ios-xcframework.sh"
fi

bash "$ROOT/scripts/setup-mihomo-ios-native.sh"

if ! command -v xcodegen >/dev/null 2>&1; then
  brew install xcodegen
fi

cd "$IOS"
xcodegen generate

mkdir -p "$BUILD_DIR"
rm -rf "$ARCHIVE_PATH" "$EXPORT_DIR"

# 生成 ExportOptions.plist
cp "$TEMPLATE_PLIST" "$EXPORT_PLIST"
/usr/libexec/PlistBuddy -c "Set :teamID $APPLE_TEAM_ID" "$EXPORT_PLIST"
/usr/libexec/PlistBuddy -c "Set :method $IOS_EXPORT_METHOD" "$EXPORT_PLIST"

SIGN_IDENTITY="${IOS_CODE_SIGN_IDENTITY:-Apple Distribution}"

echo "==> xcodebuild archive ..."
xcodebuild \
  -project KuayunVPN.xcodeproj \
  -scheme KuayunVPN \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  CODE_SIGN_STYLE=Manual \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" \
  CODE_SIGN_IDENTITY="$SIGN_IDENTITY" \
  archive

echo "==> xcodebuild exportArchive ..."
xcodebuild \
  -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$EXPORT_DIR" \
  -exportOptionsPlist "$EXPORT_PLIST"

IPA="$(find "$EXPORT_DIR" -maxdepth 1 -name '*.ipa' | head -1)"
if [[ -z "$IPA" || ! -f "$IPA" ]]; then
  echo "ERROR: IPA not found under $EXPORT_DIR" >&2
  exit 1
fi

MARKETING_VERSION="$(grep 'MARKETING_VERSION:' "$IOS/project.yml" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
OUT_NAME="kuayun-ios-${MARKETING_VERSION}.ipa"
OUT_PATH="$BUILD_DIR/$OUT_NAME"
cp "$IPA" "$OUT_PATH"

echo "IPA ready: $OUT_PATH"
echo "IOS_IPA_PATH=$OUT_PATH"
