#!/usr/bin/env bash
# macOS / Linux 桌面打包
# 用法: desktop-build-unix.sh [dmg|deb|appimage|app]
# 省略参数时：Darwin → dmg，Linux → deb
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BUNDLE="${1:-auto}"

if [[ -f "$HOME/.cargo/env" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.cargo/env"
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "cargo not found. Install Rust from https://rustup.rs" >&2
  exit 1
fi

bash "$ROOT/scripts/fetch-mihomo.sh"

OS="$(uname -s)"
if [[ "$BUNDLE" == "auto" ]]; then
  case "$OS" in
    Darwin) BUNDLE="dmg" ;;
    Linux) BUNDLE="deb" ;;
    *)
      echo "Unsupported OS for desktop-build-unix.sh: $OS" >&2
      exit 1
      ;;
  esac
fi

echo "Building Tauri desktop bundle: $BUNDLE (OS=$OS)"

# createUpdaterArtifacts + pubkey 时需要私钥；tauri build 读取 TAURI_SIGNING_PRIVATE_KEY（可为路径或内容）
if [[ -z "${TAURI_SIGNING_PRIVATE_KEY:-}" ]]; then
  UPDATER_KEY="$ROOT/.tauri/updater.key"
  if [[ -f "$UPDATER_KEY" ]]; then
    export TAURI_SIGNING_PRIVATE_KEY="$UPDATER_KEY"
    echo "Using updater signing key: $UPDATER_KEY"
  else
    echo "WARNING: .tauri/updater.key missing. Run: npm run setup:updater" >&2
  fi
fi

if [[ -z "${TAURI_SIGNING_PRIVATE_KEY_PASSWORD:-}" && -f "$ROOT/.tauri/updater.key.password" ]]; then
  export TAURI_SIGNING_PRIVATE_KEY_PASSWORD="$(tr -d '\r\n' < "$ROOT/.tauri/updater.key.password")"
  echo "Using updater password from: $ROOT/.tauri/updater.key.password"
fi

npx tauri build --bundles "$BUNDLE"
