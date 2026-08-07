#!/usr/bin/env bash
# CI：配置 Android Release 签名。
# 优先级：
# 1) 仓库内已有 keystore.properties + keystore，且未强制 Secrets → 直接用
# 2) 仓库内已有 keystore 文件 + 密码 Secrets → 只写 properties（无需 BASE64）
# 3) ANDROID_KEYSTORE_BASE64 + 密码 Secrets → 解码写入
set -euo pipefail

ANDROID_DIR="${1:-apps/android}"
KEYSTORE_DIR="$ANDROID_DIR/keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/kuayun-release.keystore"
PROPS_FILE="$ANDROID_DIR/keystore.properties"

write_props() {
  cat >"$PROPS_FILE" <<EOF
storeFile=keystore/kuayun-release.keystore
storePassword=${ANDROID_KEYSTORE_PASSWORD}
keyAlias=${ANDROID_KEY_ALIAS}
keyPassword=${ANDROID_KEY_PASSWORD}
EOF
}

require_password_secrets() {
  local missing=0
  for var in ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
    if [[ -z "${!var:-}" ]]; then
      echo "ERROR: $var is not set" >&2
      missing=1
    fi
  done
  if [[ "$missing" -ne 0 ]]; then
    echo "HINT: 在 GitHub Secrets 配置 ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD" >&2
    echo "      若仓库未跟踪 keystore，还需 ANDROID_KEYSTORE_BASE64" >&2
    exit 1
  fi
}

if [[ -f "$PROPS_FILE" && -f "$KEYSTORE_FILE" && "${ANDROID_FORCE_SECRET_SIGNING:-}" != "1" ]]; then
  echo "Using checked-in Android signing materials: $PROPS_FILE"
  exit 0
fi

# 私有仓常只跟踪 .keystore（properties 在 .gitignore）：用密码 Secrets 补 properties
if [[ -f "$KEYSTORE_FILE" && "${ANDROID_FORCE_SECRET_SIGNING:-}" != "1" ]]; then
  if [[ -n "${ANDROID_KEYSTORE_PASSWORD:-}" && -n "${ANDROID_KEY_ALIAS:-}" && -n "${ANDROID_KEY_PASSWORD:-}" ]]; then
    write_props
    echo "Using checked-in keystore + Secrets passwords: $KEYSTORE_FILE"
    exit 0
  fi
  # 有实体 keystore 但缺密码 Secrets：若也没有 BASE64，直接报清错
  if [[ -z "${ANDROID_KEYSTORE_BASE64:-}" ]]; then
    echo "ERROR: found $KEYSTORE_FILE but keystore.properties is missing" >&2
    echo "       Set ANDROID_KEYSTORE_PASSWORD + ANDROID_KEY_ALIAS + ANDROID_KEY_PASSWORD" >&2
    echo "       (or commit keystore.properties / provide ANDROID_KEYSTORE_BASE64)" >&2
    exit 1
  fi
fi

if [[ -z "${ANDROID_KEYSTORE_BASE64:-}" ]]; then
  echo "ERROR: ANDROID_KEYSTORE_BASE64 is not set and no usable checked-in keystore found" >&2
  echo "       expected keystore at: $KEYSTORE_FILE" >&2
  exit 1
fi

require_password_secrets

mkdir -p "$KEYSTORE_DIR"
# 去掉换行/空格，避免 GitHub Secret 粘贴时引入空白导致解码损坏
echo "$ANDROID_KEYSTORE_BASE64" | tr -d '\n\r\t ' | base64 --decode >"$KEYSTORE_FILE"
write_props

echo "Android signing configured from Secrets: $PROPS_FILE"
