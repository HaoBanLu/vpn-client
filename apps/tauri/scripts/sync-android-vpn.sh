#!/usr/bin/env bash
# 将 src-tauri/android VPN 覆盖层同步到 gen/android
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OVERLAY_MAIN="$ROOT/src-tauri/android/app/src/main"
GEN_MAIN="$ROOT/src-tauri/gen/android/app/src/main"

if [[ ! -d "$ROOT/src-tauri/gen/android/app" ]]; then
  echo "gen/android 不存在，请先运行: npx tauri android init --ci" >&2
  exit 1
fi

mkdir -p "$GEN_MAIN/java/com/vpn/tauri"
rm -rf "$GEN_MAIN/java/com/vpn/tauri/vpn"
cp -R "$OVERLAY_MAIN/java/com/vpn/tauri/vpn" "$GEN_MAIN/java/com/vpn/tauri/"

python3 - "$OVERLAY_MAIN/AndroidManifest.xml" "$GEN_MAIN/AndroidManifest.xml" <<'PY'
import re, sys
overlay_path, gen_path = sys.argv[1], sys.argv[2]
marker = "<!-- vpn-tauri-overlay -->"
gen = open(gen_path, encoding="utf-8").read()
if marker in gen:
    sys.exit(0)
overlay = open(overlay_path, encoding="utf-8").read()
perms = "\n    ".join(dict.fromkeys(re.findall(r"<uses-permission[^>]+>", overlay)))
if perms:
    gen = re.sub(r"(<manifest[^>]*>)", r"\1\n    " + perms, gen, count=1)
m = re.search(r"<application[^>]*>(.*)</application>", overlay, re.S)
if m:
    block = m.group(1).strip()
    insert = f"        {marker}\n        {block}\n        {marker}"
    gen = gen.replace("</application>", insert + "\n    </application>")
open(gen_path, "w", encoding="utf-8").write(gen)
PY

GRADLE="$ROOT/src-tauri/gen/android/app/build.gradle.kts"
APPLY='apply(from = file("../../../android/app/build.gradle.kts"))'
if ! grep -qF "$APPLY" "$GRADLE"; then
  printf '\n%s\n' "$APPLY" >> "$GRADLE"
fi

echo "Synced VPN overlay -> gen/android"
