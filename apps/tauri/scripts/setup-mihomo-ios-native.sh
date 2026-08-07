#!/usr/bin/env bash
# 安装 iOS Mihomo.xcframework 到 PacketTunnel，并同步 project.yml 依赖。
# 用法（macOS）：cd apps/tauri && npm run tauri:ios:setup-native
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/platforms/ios"
VENDOR_XCFW="$IOS/vendor/Mihomo.xcframework"
DEST_DIR="$IOS/PacketTunnel/Frameworks"
DEST="$DEST_DIR/Mihomo.xcframework"
PROJECT_YML="$IOS/project.yml"
FLAG_FILE="$IOS/.mihomo_native_enabled"

mkdir -p "$DEST_DIR"

cat >"$DEST_DIR/README.md" <<'EOF'
# PacketTunnel Frameworks

```bash
cd apps/tauri
npm run tauri:ios:build-xcframework   # 可选
# 或手动: 复制 Mihomo.xcframework → platforms/ios/vendor/
npm run tauri:ios:setup-native
npm run tauri:ios:generate && npm run tauri:ios:build
```

文档：`docs/product/iOS-Mihomo-xcframework接入.md`
EOF

HAS_XCFW=0
if [[ -d "$VENDOR_XCFW" ]]; then
  echo "Installing Mihomo.xcframework from vendor ..."
  rm -rf "$DEST"
  cp -R "$VENDOR_XCFW" "$DEST"
  HAS_XCFW=1
elif [[ -d "$DEST" ]]; then
  echo "Using existing $DEST"
  HAS_XCFW=1
else
  echo "WARN: no Mihomo.xcframework (vendor/ or Frameworks/)."
  echo "      Connect will fail until you build/copy one."
  echo "      Try: npm run tauri:ios:build-xcframework"
fi

python3 - "$PROJECT_YML" "$HAS_XCFW" "$FLAG_FILE" <<'PY'
import pathlib, re, sys
yml_path = pathlib.Path(sys.argv[1])
has = sys.argv[2] == "1"
flag = pathlib.Path(sys.argv[3])
text = yml_path.read_text(encoding="utf-8")

# 去掉旧标记块
text = re.sub(
    r"\n    # BEGIN MIHOMO_NATIVE.*?# END MIHOMO_NATIVE\n",
    "\n",
    text,
    flags=re.S,
)
# 去掉旧的 MIHOMO_NATIVE 编译条件
text = text.replace(
    "\n        SWIFT_ACTIVE_COMPILATION_CONDITIONS: $(inherited) MIHOMO_NATIVE",
    "",
)

if has:
    flag.write_text("1\n", encoding="utf-8")
    dep = """
    # BEGIN MIHOMO_NATIVE
    dependencies:
      - framework: PacketTunnel/Frameworks/Mihomo.xcframework
        embed: false
    # END MIHOMO_NATIVE
"""
    # 插在 PacketTunnel 的 info 属性结束后、schemes 前
    m = re.search(
        r"(  PacketTunnel:.*?NSExtensionPrincipalClass: \$\{PRODUCT_MODULE_NAME\}\.PacketTunnelProvider\n)",
        text,
        flags=re.S,
    )
    if not m:
        raise SystemExit("project.yml: PacketTunnel block anchor not found")
    text = text[: m.end(1)] + dep + text[m.end(1) :]
    # settings.base 增加编译条件
    old = "        CODE_SIGN_ENTITLEMENTS: PacketTunnel/PacketTunnel.entitlements\n"
    new = old + "        SWIFT_ACTIVE_COMPILATION_CONDITIONS: $(inherited) MIHOMO_NATIVE\n"
    if old not in text:
        raise SystemExit("project.yml: PacketTunnel CODE_SIGN_ENTITLEMENTS not found")
    # 只替换 PacketTunnel 段内第一次出现：用 count 在 PacketTunnel 之后
    idx = text.find("  PacketTunnel:")
    pre, post = text[:idx], text[idx:]
    post = post.replace(old, new, 1)
    text = pre + post
    print("Patched project.yml: linked Mihomo.xcframework + MIHOMO_NATIVE")
else:
    if flag.exists():
        flag.unlink()
    print("project.yml: Mihomo native dependency cleared (no xcframework)")

yml_path.write_text(text, encoding="utf-8")
PY

echo "iOS native setup done."
echo "Next: npm run tauri:ios:generate && npm run tauri:ios:build"
echo "Docs: docs/product/iOS-Mihomo-xcframework接入.md"
