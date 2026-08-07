#!/usr/bin/env bash
# CI：导入 Apple Distribution 证书与 Provisioning Profile 到临时钥匙串
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "ERROR: Apple signing setup requires macOS" >&2
  exit 1
fi

for var in APPLE_CERTIFICATE_BASE64 APPLE_CERTIFICATE_PASSWORD APPLE_TEAM_ID KEYCHAIN_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set" >&2
    exit 1
  fi
done

KEYCHAIN_PATH="$RUNNER_TEMP/app-signing.keychain-db"
CERT_PATH="$RUNNER_TEMP/distribution.p12"
PROFILE_DIR="$HOME/Library/MobileDevice/Provisioning Profiles"

security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

echo "$APPLE_CERTIFICATE_BASE64" | base64 --decode >"$CERT_PATH"
security import "$CERT_PATH" -P "$APPLE_CERTIFICATE_PASSWORD" -A -t cert -f pkcs12 -k "$KEYCHAIN_PATH"
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security list-keychain -d user -s "$KEYCHAIN_PATH"

mkdir -p "$PROFILE_DIR"

install_profile() {
  local b64="$1"
  local label="$2"
  if [[ -z "$b64" ]]; then
    echo "WARN: $label profile not set, skipping"
    return 0
  fi
  local tmp="$RUNNER_TEMP/${label}.mobileprovision"
  echo "$b64" | base64 --decode >"$tmp"
  local uuid
  uuid="$(/usr/libexec/PlistBuddy -c 'Print UUID' /dev/stdin <<< "$(security cms -D -i "$tmp")")"
  cp "$tmp" "$PROFILE_DIR/${uuid}.mobileprovision"
  echo "Installed $label profile: $uuid"
}

install_profile "${APPLE_PROVISIONING_PROFILE_APP_BASE64:-}" "app"
install_profile "${APPLE_PROVISIONING_PROFILE_TUNNEL_BASE64:-}" "tunnel"

echo "Apple signing ready (team=$APPLE_TEAM_ID)"
