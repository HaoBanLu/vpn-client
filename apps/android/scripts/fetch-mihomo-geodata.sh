#!/usr/bin/env bash
# 下载 Mihomo 离线 geodata / ruleset，避免首连从 GitHub/CDN 拉取失败（国内常见）。
# 用法：在 apps/android 目录执行 bash scripts/fetch-mihomo-geodata.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/mihomo"
RULESET="$ASSETS/ruleset"
GEO_BASE="https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release"
RULE_BASE="https://cdn.jsdelivr.net/gh/Loyalsoldier/clash-rules@release"

mkdir -p "$ASSETS" "$RULESET"

fetch_if_stale() {
  local url="$1"
  local dest="$2"
  local tmp="$dest.tmp"
  if [[ -f "$dest" && $(find "$dest" -mtime -7 2>/dev/null | wc -l) -gt 0 ]]; then
    echo "==> $(basename "$dest") exists (skip)"
    return
  fi
  echo "==> download $(basename "$dest")"
  curl -fsSL -o "$tmp" "$url"
  mv "$tmp" "$dest"
}

fetch_if_stale "$GEO_BASE/geosite.dat" "$ASSETS/geosite.dat"
fetch_if_stale "$GEO_BASE/geoip.metadb" "$ASSETS/geoip.metadb"
fetch_if_stale "$RULE_BASE/reject.txt" "$RULESET/reject.yaml"
fetch_if_stale "$RULE_BASE/direct.txt" "$RULESET/cn.yaml"

echo "Done. Bundled assets at $ASSETS"
