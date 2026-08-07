#!/usr/bin/env bash
# macOS / Linux 桌面开发：补 cargo PATH → tauri dev
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f "$HOME/.cargo/env" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.cargo/env"
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "cargo not found. Install Rust from https://rustup.rs" >&2
  exit 1
fi

if [[ ! -x "$ROOT/src-tauri/resources/bin/mihomo" ]] && [[ ! -f "$ROOT/src-tauri/resources/bin/mihomo.exe" ]]; then
  echo "mihomo missing; running fetch-mihomo.sh ..."
  bash "$ROOT/scripts/fetch-mihomo.sh"
fi

exec npx tauri dev
