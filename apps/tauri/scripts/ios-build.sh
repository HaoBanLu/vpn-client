#!/usr/bin/env bash
# iPhone 工程：xcodegen + xcodebuild（Simulator，无需签名）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/platforms/ios"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iOS build requires macOS + Xcode." >&2
  exit 1
fi

cd "$IOS"

bash "$ROOT/scripts/fetch-mihomo-ios.sh"
bash "$ROOT/scripts/setup-mihomo-ios-native.sh"

if ! command -v xcodegen >/dev/null 2>&1; then
  if command -v brew >/dev/null 2>&1; then
    echo "Installing xcodegen via Homebrew ..."
    brew install xcodegen
  else
    echo "xcodegen not found. Install: brew install xcodegen" >&2
    exit 1
  fi
fi

echo "Generating KuayunVPN.xcodeproj ..."
xcodegen generate

if [[ ! -d "KuayunVPN.xcodeproj" ]]; then
  echo "KuayunVPN.xcodeproj not found after xcodegen" >&2
  exit 1
fi

echo "Building for iOS Simulator (no code signing) ..."
xcodebuild \
  -project KuayunVPN.xcodeproj \
  -scheme KuayunVPN \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -configuration Debug \
  build \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO

echo "iOS build OK"
