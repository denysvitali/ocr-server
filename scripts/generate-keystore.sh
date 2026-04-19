#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_FILE="$(dirname "$0")/../app/src/main/res/raw/keystore.p12"
PASSWORD="changeit"
ALIAS="alias"
VALIDITY=36500

mkdir -p "$(dirname "$KEYSTORE_FILE")"

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY" \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=OCR Server,OU=Dev,O=Dev,L=Zurich,ST=Zurich,C=CH"

echo ""
echo "Keystore generated at: $KEYSTORE_FILE"
echo ""
echo "To store as a GitHub secret, run:"
echo ""
echo "  gh secret set KEYSTORE_BASE64 < <(base64 -w0 "$KEYSTORE_FILE")"
echo ""
echo "Base64 value (copy to clipboard if not using gh):"
base64 -w0 "$KEYSTORE_FILE"
echo ""
