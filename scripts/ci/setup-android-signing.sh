#!/usr/bin/env bash
# CI：配置 Android Release 签名。
# 私有仓库若已跟踪 keystore.properties + keystore 文件，默认直接使用，避免 Secrets Base64 覆盖坏包。
# 仅当 ANDROID_FORCE_SECRET_SIGNING=1 或仓库内签名文件缺失时，才从 Secrets 解码写入。
set -euo pipefail

ANDROID_DIR="${1:-apps/android}"
KEYSTORE_DIR="$ANDROID_DIR/keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/kuayun-release.keystore"
PROPS_FILE="$ANDROID_DIR/keystore.properties"

if [[ -f "$PROPS_FILE" && -f "$KEYSTORE_FILE" && "${ANDROID_FORCE_SECRET_SIGNING:-}" != "1" ]]; then
  echo "Using checked-in Android signing materials: $PROPS_FILE"
  exit 0
fi

if [[ -z "${ANDROID_KEYSTORE_BASE64:-}" ]]; then
  echo "ERROR: ANDROID_KEYSTORE_BASE64 is not set (and no checked-in keystore found)" >&2
  exit 1
fi

for var in ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set" >&2
    exit 1
  fi
done

mkdir -p "$KEYSTORE_DIR"
# 去掉换行/空格，避免 GitHub Secret 粘贴时引入空白导致解码损坏
echo "$ANDROID_KEYSTORE_BASE64" | tr -d '\n\r\t ' | base64 --decode >"$KEYSTORE_FILE"

cat >"$PROPS_FILE" <<EOF
storeFile=keystore/kuayun-release.keystore
storePassword=${ANDROID_KEYSTORE_PASSWORD}
keyAlias=${ANDROID_KEY_ALIAS}
keyPassword=${ANDROID_KEY_PASSWORD}
EOF

echo "Android signing configured from Secrets: $PROPS_FILE"
