#!/usr/bin/env bash
# 将 src-tauri/android VPN 覆盖层同步到 gen/android（含 mihomo-core 依赖声明）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OVERLAY_APP="$ROOT/src-tauri/android/app"
OVERLAY_MAIN="$OVERLAY_APP/src/main"
GEN_APP="$ROOT/src-tauri/gen/android/app"
GEN_MAIN="$GEN_APP/src/main"
GEN_ANDROID="$ROOT/src-tauri/gen/android"

if [[ ! -d "$GEN_APP" ]]; then
  echo "gen/android 不存在，请先运行: npx tauri android init --ci" >&2
  exit 1
fi

mkdir -p "$GEN_MAIN/java/com/vpn/kuayun"
rm -rf "$GEN_MAIN/java/com/vpn/kuayun/vpn"
cp -R "$OVERLAY_MAIN/java/com/vpn/kuayun/vpn" "$GEN_MAIN/java/com/vpn/kuayun/"
# 清理旧包名残留
rm -rf "$GEN_MAIN/java/com/vpn/tauri"
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

GRADLE="$GEN_APP/build.gradle.kts"
APPLY='apply(from = file("../../../android/app/build.gradle.kts"))'
if ! grep -qF "$APPLY" "$GRADLE"; then
  printf '\n%s\n' "$APPLY" >> "$GRADLE"
fi

OVERLAY_PROGUARD="$OVERLAY_APP/proguard-rules.pro"
if [[ -f "$OVERLAY_PROGUARD" ]]; then
  cp -f "$OVERLAY_PROGUARD" "$GEN_APP/proguard-rules.pro"
fi

GRADLE_PROPS="$GEN_ANDROID/gradle.properties"
if [[ -f "$GRADLE_PROPS" ]] && ! grep -q 'kotlin.compiler.execution.strategy' "$GRADLE_PROPS"; then
  printf '\nkotlin.compiler.execution.strategy=in-process\n' >> "$GRADLE_PROPS"
fi

SETTINGS="$GEN_ANDROID/settings.gradle.kts"
if [[ -f "$SETTINGS" ]] && ! grep -q 'include(":mihomo-core")' "$SETTINGS"; then
  # 存档工程 apps/android/mihomo-core：仍作为 JNI 模块依赖，勿删
  cat >> "$SETTINGS" <<'EOF'

include(":mihomo-core")
project(":mihomo-core").projectDir = file("../../../../android/mihomo-core")
EOF
fi

ROOT_BUILD="$GEN_ANDROID/build.gradle.kts"
if [[ -f "$ROOT_BUILD" ]] && ! grep -q 'kotlin.plugin.serialization' "$ROOT_BUILD"; then
  python3 - "$ROOT_BUILD" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
t = p.read_text(encoding="utf-8")
needle = 'id("org.jetbrains.kotlin.android")'
if needle in t and "kotlin.plugin.serialization" not in t:
    t = t.replace(
        needle,
        needle + '\n    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false',
        1,
    )
    p.write_text(t, encoding="utf-8")
PY
fi

MIHOMO_JNI="$ROOT/../android/mihomo-core/src/main/jniLibs"
if [[ ! -d "$MIHOMO_JNI/arm64-v8a" ]]; then
  echo "WARN: mihomo jniLibs missing under apps/android/mihomo-core; run setup-mihomo-native.sh" >&2
fi

echo "Synced VPN overlay -> gen/android"
