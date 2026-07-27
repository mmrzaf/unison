#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

command -v keytool >/dev/null 2>&1 || { echo "keytool is required (install JDK 21)." >&2; exit 1; }
[[ ! -e keystore.properties ]] || { echo "keystore.properties already exists; refusing to overwrite it." >&2; exit 1; }
mkdir -p keystore
[[ ! -e keystore/unison-release.jks ]] || { echo "keystore/unison-release.jks already exists; refusing to overwrite it." >&2; exit 1; }

read -r -p "Key alias [unison]: " KEY_ALIAS
KEY_ALIAS="${KEY_ALIAS:-unison}"
read -r -s -p "Keystore password: " STORE_PASSWORD; echo
read -r -s -p "Repeat keystore password: " STORE_PASSWORD_CONFIRM; echo
[[ "$STORE_PASSWORD" == "$STORE_PASSWORD_CONFIRM" ]] || { echo "Passwords do not match." >&2; exit 1; }
[[ ${#STORE_PASSWORD} -ge 8 ]] || { echo "Use a password of at least 8 characters." >&2; exit 1; }
read -r -s -p "Key password [same as keystore]: " KEY_PASSWORD; echo
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

keytool -genkeypair \
  -keystore keystore/unison-release.jks \
  -storepass "$STORE_PASSWORD" \
  -alias "$KEY_ALIAS" \
  -keypass "$KEY_PASSWORD" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Unison, OU=Android, O=Darius, C=AZ"

umask 077
cat > keystore.properties <<PROPS
storeFile=keystore/unison-release.jks
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
PROPS

echo "Created keystore/unison-release.jks and keystore.properties. Back up both securely."
