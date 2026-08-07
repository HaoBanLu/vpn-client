#!/usr/bin/env bash
# 生成本地 Release Keystore，并写入 local.properties 签名项（不提交 git）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE_DIR="$ROOT/keystore"
KEYSTORE="$KEYSTORE_DIR/kuayun-release.keystore"
ALIAS="kuayun"
LOCAL_PROPS="$ROOT/local.properties"

if [[ -f "$KEYSTORE" ]]; then
  echo "Keystore 已存在: $KEYSTORE"
  echo "如需重建请先手动删除该文件。"
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"

read -r -s -p "Keystore 密码: " STORE_PW
echo
read -r -s -p "Key 密码（直接回车则与 Keystore 相同）: " KEY_PW
echo
KEY_PW="${KEY_PW:-$STORE_PW}"

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STORE_PW" -keypass "$KEY_PW" \
  -dname "CN=Kuayun VPN, OU=Mobile, O=Kuayun, L=Hangzhou, ST=Zhejiang, C=CN"

touch "$LOCAL_PROPS"

set_prop() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$LOCAL_PROPS" 2>/dev/null; then
    if [[ "$(uname -s)" == "Darwin" ]]; then
      sed -i '' "s|^${key}=.*|${key}=${value}|" "$LOCAL_PROPS"
    else
      sed -i "s|^${key}=.*|${key}=${value}|" "$LOCAL_PROPS"
    fi
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$LOCAL_PROPS"
  fi
}

set_prop "release.storeFile" "keystore/kuayun-release.keystore"
set_prop "release.storePassword" "$STORE_PW"
set_prop "release.keyAlias" "$ALIAS"
set_prop "release.keyPassword" "$KEY_PW"

echo ""
echo "已生成: $KEYSTORE"
echo "签名配置已写入: $LOCAL_PROPS"
echo "请妥善备份 Keystore 与密码；丢失后无法为同一 applicationId 发布更新。"
