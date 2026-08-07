#!/usr/bin/env bash
# 在 macOS 上构建真实 Mihomo.xcframework（链入 metacubex/mihomo 引擎）。
# 用法：cd apps/tauri && npm run tauri:ios:build-xcframework
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/platforms/ios"
BRIDGE_SRC="$IOS/native/mihomo-bridge"
VENDOR="$IOS/vendor"
OUT_XCFW="$VENDOR/Mihomo.xcframework"
WORK="${MIHOMO_IOS_WORK:-$ROOT/.cache/mihomo-ios-build}"
MIHOMO_VERSION="${MIHOMO_VERSION:-v1.19.0}"

echo "==> Build Mihomo.xcframework (real engine)"
echo "    bridge: $BRIDGE_SRC"
echo "    mihomo: $MIHOMO_VERSION"
echo "    out:    $OUT_XCFW"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "ERROR: 必须在 macOS 上构建 iOS xcframework（当前: $(uname -s)）"
  echo "在 Mac 执行本命令后，Windows 仓库可直接提交 vendor 以外的源码；"
  echo "产物放 platforms/ios/vendor/Mihomo.xcframework（gitignore）。"
  exit 1
fi

command -v xcodebuild >/dev/null || { echo "ERROR: 需要 Xcode CLI"; exit 1; }
command -v go >/dev/null || { echo "ERROR: 需要 Go 1.22+"; exit 1; }
command -v clang >/dev/null || { echo "ERROR: 需要 clang"; exit 1; }

[[ -f "$BRIDGE_SRC/bridge.go" ]] || { echo "ERROR: missing bridge.go"; exit 1; }
[[ -f "$IOS/native/mihomo_bridge.h" ]] || { echo "ERROR: missing mihomo_bridge.h"; exit 1; }

rm -rf "$WORK"
mkdir -p "$WORK/src" "$WORK/out/device" "$WORK/out/sim" "$VENDOR"
cp -R "$BRIDGE_SRC/." "$WORK/src/"
cp "$IOS/native/mihomo_bridge.h" "$WORK/src/mihomo_bridge.h"

(
  cd "$WORK/src"
  go mod edit -go=1.22
  go get "github.com/metacubex/mihomo@${MIHOMO_VERSION}"
  go mod tidy
)

DEV_SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
SIM_SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"
CLANG_IOS="$(xcrun --sdk iphoneos --find clang)"
CLANG_SIM="$(xcrun --sdk iphonesimulator --find clang)"

build_one() {
  local sdk="$1" clang="$2" goarch="$3" outdir="$4" minver_flag="$5"
  echo "    building $outdir ($goarch) ..."
  mkdir -p "$outdir"
  (
    cd "$WORK/src"
    # ios 目标：Go 1.22+ 使用 GOOS=ios
    CGO_ENABLED=1 \
      GOOS=ios \
      GOARCH="$goarch" \
      CC="$clang" \
      CXX="$clang" \
      CGO_CFLAGS="-isysroot ${sdk} ${minver_flag} -fembed-bitcode-marker" \
      CGO_LDFLAGS="-isysroot ${sdk} ${minver_flag}" \
      go build -buildmode=c-archive -trimpath -o "$outdir/libmihomo.a" .
  )
  mkdir -p "$outdir/Headers"
  cp "$IOS/native/mihomo_bridge.h" "$outdir/Headers/"
}

build_one "$DEV_SDK" "$CLANG_IOS" "arm64" "$WORK/out/device" "-miphoneos-version-min=16.0"
build_one "$SIM_SDK" "$CLANG_SIM" "arm64" "$WORK/out/sim" "-mios-simulator-version-min=16.0" \
  || echo "WARN: simulator arm64 build failed; device slice only"

echo "==> create-xcframework"
rm -rf "$OUT_XCFW"
ARGS=( -create-xcframework -library "$WORK/out/device/libmihomo.a" -headers "$WORK/out/device/Headers" )
if [[ -f "$WORK/out/sim/libmihomo.a" ]]; then
  ARGS+=( -library "$WORK/out/sim/libmihomo.a" -headers "$WORK/out/sim/Headers" )
fi
ARGS+=( -output "$OUT_XCFW" )
xcodebuild "${ARGS[@]}"

# 模块映射，便于 Swift/Clang 找到头文件
cat >"$OUT_XCFW/Modules/module.modulemap" <<'EOF' 2>/dev/null || true
# module map may need per-slice placement; headers already in -headers
EOF

echo ""
echo "OK: $OUT_XCFW"
echo "Next:"
echo "  npm run tauri:ios:setup-native"
echo "  npm run tauri:ios:generate && npm run tauri:ios:build"
echo "真机：连接后应走 mixed-port + NEProxySettings 出网"
