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

# 同步 overlay 资源（FileProvider paths 等）
if [[ -d "$OVERLAY_MAIN/res" ]]; then
  mkdir -p "$GEN_MAIN/res"
  cp -R "$OVERLAY_MAIN/res/." "$GEN_MAIN/res/"
fi

python3 - "$OVERLAY_MAIN/AndroidManifest.xml" "$GEN_MAIN/AndroidManifest.xml" <<'PY'
import re, sys
overlay_path, gen_path = sys.argv[1], sys.argv[2]
marker = "<!-- vpn-tauri-overlay -->"
gen = open(gen_path, encoding="utf-8").read()
overlay = open(overlay_path, encoding="utf-8").read()
perms = list(dict.fromkeys(re.findall(r"<uses-permission[^>]+>", overlay)))
missing = [p for p in perms if p not in gen]
if missing:
    gen = re.sub(r"(<manifest[^>]*>)", r"\1\n    " + "\n    ".join(missing), gen, count=1)
m = re.search(r"<application[^>]*>(.*)</application>", overlay, re.S)
if m:
    block = m.group(1).strip()
    insert = f"        {marker}\n        {block}\n        {marker}"
    if marker in gen:
        gen = re.sub(
            re.escape(marker) + r".*?" + re.escape(marker),
            insert,
            gen,
            count=1,
            flags=re.S,
        )
    else:
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

SETTINGS_KTS="$GEN_ANDROID/settings.gradle.kts"
SETTINGS_GROOVY="$GEN_ANDROID/settings.gradle"
MIHOMO_INCLUDE=$'include(":mihomo-core")\nproject(":mihomo-core").projectDir = file("../../../../android/mihomo-core")\n'
# 与 Tauri gen 工程常见 Kotlin 版本对齐（日志见 1.9.25）
SERIALIZATION_VER="${KUAYUN_KOTLIN_SERIALIZATION_VERSION:-1.9.25}"

patch_settings() {
  local settings="$1"
  if [[ -f "$settings" ]] && ! grep -q 'include(":mihomo-core")' "$settings"; then
    # 存档工程 apps/android/mihomo-core：仍作为 JNI 模块依赖，勿删
    # Tauri 2 当前生成 settings.gradle（Groovy）；旧模板可能是 .kts
    printf '\n%s' "$MIHOMO_INCLUDE" >> "$settings"
    echo "Patched mihomo-core into $settings"
  fi
}

patch_settings "$SETTINGS_KTS"
patch_settings "$SETTINGS_GROOVY"

if ! grep -rq 'include(":mihomo-core")' "$GEN_ANDROID"/settings.gradle* 2>/dev/null; then
  echo "ERROR: failed to include :mihomo-core in gen/android settings.gradle*" >&2
  ls -la "$GEN_ANDROID"/settings.gradle* 2>/dev/null || true
  exit 1
fi

# mihomo-core 需要 kotlin serialization 插件版本；Tauri 根工程默认不声明
ensure_serialization_plugin() {
  local root_build="$GEN_ANDROID/build.gradle.kts"
  local settings_groovy="$GEN_ANDROID/settings.gradle"
  local marker='org.jetbrains.kotlin.plugin.serialization'

  if [[ -f "$root_build" ]] && ! grep -q "$marker" "$root_build"; then
    python3 - "$root_build" "$SERIALIZATION_VER" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
ver = sys.argv[2]
t = p.read_text(encoding="utf-8")
line = f'    id("org.jetbrains.kotlin.plugin.serialization") version "{ver}" apply false'
if "plugins {" in t:
    t = t.replace("plugins {", "plugins {\n" + line, 1)
else:
    t = f"plugins {{\n{line}\n}}\n\n" + t
p.write_text(t, encoding="utf-8")
print(f"Patched serialization plugin into {p}")
PY
  fi

  # settings.gradle pluginManagement 再兜一层（部分模板根 build 无 plugins 块）
  if [[ -f "$settings_groovy" ]] && ! grep -q "$marker" "$settings_groovy"; then
    python3 - "$settings_groovy" "$SERIALIZATION_VER" <<'PY'
from pathlib import Path
import re, sys
p = Path(sys.argv[1])
ver = sys.argv[2]
t = p.read_text(encoding="utf-8")
plugin_line = f"        id 'org.jetbrains.kotlin.plugin.serialization' version '{ver}'\n"
# 在 pluginManagement { ... } 内插入 plugins { ... }
m = re.search(r"pluginManagement\s*\{", t)
if not m:
    t = "pluginManagement {\n    plugins {\n" + plugin_line + "    }\n}\n" + t
else:
    # 若已有 plugins { 则插入其中，否则在 pluginManagement 开头加
    rest = t[m.end():]
    pm_plugins = re.search(r"plugins\s*\{", rest)
    if pm_plugins and pm_plugins.start() < rest.find("}"):
        idx = m.end() + pm_plugins.end()
        t = t[:idx] + "\n" + plugin_line + t[idx:]
    else:
        idx = m.end()
        t = t[:idx] + "\n    plugins {\n" + plugin_line + "    }\n" + t[idx:]
p.write_text(t, encoding="utf-8")
print(f"Patched serialization plugin into {p}")
PY
  fi
}

ensure_serialization_plugin

if ! grep -rq 'kotlin.plugin.serialization' "$GEN_ANDROID"/build.gradle.kts "$GEN_ANDROID"/settings.gradle 2>/dev/null; then
  echo "ERROR: kotlin.plugin.serialization not declared for gen/android" >&2
  exit 1
fi

# mihomo-core minSdk=26；若 init 时未读到 tauri.conf android.minSdkVersion，这里兜底改 gen
APP_GRADLE="$GEN_APP/build.gradle.kts"
if [[ -f "$APP_GRADLE" ]]; then
  python3 - "$APP_GRADLE" <<'PY'
from pathlib import Path
import re, sys
p = Path(sys.argv[1])
t = p.read_text(encoding="utf-8")
nt, n = re.subn(r"minSdk\s*=\s*\d+", "minSdk = 26", t, count=1)
if n:
    p.write_text(nt, encoding="utf-8")
    print(f"Patched minSdk=26 into {p}")
elif "minSdk" not in t:
    print(f"WARN: minSdk not found in {p}", file=sys.stderr)
PY
fi

MIHOMO_JNI="$ROOT/../android/mihomo-core/src/main/jniLibs"
if [[ ! -d "$MIHOMO_JNI/arm64-v8a" ]]; then
  echo "WARN: mihomo jniLibs missing under apps/android/mihomo-core; run setup-mihomo-native.sh" >&2
fi

echo "Synced VPN overlay -> gen/android"
