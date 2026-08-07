#!/usr/bin/env bash
# iOS Mihomo 资源占位：官方 GitHub Release 不含 iOS 可执行文件。
# 真机数据面需 CMFA/mihomo-core xcframework 或自建 Go→ios 静态库（见 platforms/ios/README.md）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RES="$ROOT/platforms/ios/PacketTunnel/Resources"
mkdir -p "$RES"

README="$RES/MIHOMO_README.txt"
cat >"$README" <<'EOF'
Mihomo on iOS
=============
- MetaCubeX/mihomo releases do NOT ship ios-arm64 binaries.
- Phase B scaffold: PacketTunnel runs NE settings + config sanitizer.
- Production: embed mihomo-core xcframework (align Android mihomo-core/jniLibs).

Build pipeline (future):
  1. Build or obtain libclash / mihomo ios xcframework
  2. Copy into PacketTunnel/Frameworks/
  3. Link in Xcode + update MihomoRunner to call native bridge

Optional dev-only (NOT for App Store):
  If mihomo-ios binary is placed beside this file, ios-build.sh will copy it.
EOF

if [[ -f "$ROOT/platforms/ios/vendor/mihomo-ios" ]]; then
  cp "$ROOT/platforms/ios/vendor/mihomo-ios" "$RES/mihomo-ios"
  chmod +x "$RES/mihomo-ios"
  echo "Copied vendor/mihomo-ios into PacketTunnel/Resources"
else
  echo "No vendor/mihomo-ios; extension builds without Mihomo binary (expected)."
fi

echo "iOS Mihomo placeholder OK: $README"
