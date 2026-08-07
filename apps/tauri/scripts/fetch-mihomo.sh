#!/usr/bin/env bash
set -euo pipefail
VERSION="${MIHOMO_VERSION:-1.19.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src-tauri/resources/bin"
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
case "$OS-$ARCH" in
  linux-x86_64) ASSET="mihomo-linux-amd64-v${VERSION}.gz" ;;
  linux-aarch64|linux-arm64) ASSET="mihomo-linux-arm64-v${VERSION}.gz" ;;
  darwin-x86_64) ASSET="mihomo-darwin-amd64-v${VERSION}.gz" ;;
  darwin-arm64) ASSET="mihomo-darwin-arm64-v${VERSION}.gz" ;;
  *) echo "Unsupported platform: $OS $ARCH"; exit 1 ;;
esac

URL="https://github.com/MetaCubeX/mihomo/releases/download/v${VERSION}/${ASSET}"
echo "Downloading $URL"
curl -fsSL "$URL" -o "$TMP/archive.gz"
gunzip -c "$TMP/archive.gz" > "$TMP/mihomo"
chmod +x "$TMP/mihomo"
cp "$TMP/mihomo" "$OUT/mihomo"
echo "Installed: $OUT/mihomo"
