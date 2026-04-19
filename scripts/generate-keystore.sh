#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_FILE="$(dirname "$0")/../release.keystore"
PASSWORD="changeit"
ALIAS="release"
VALIDITY=36500

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
echo "Signing keystore generated at: $KEYSTORE_FILE"
echo ""
echo "Store the GitHub secrets:"
echo ""
echo "  gh secret set SIGNING_KEYSTORE_BASE64 < <(base64 -w0 \"$KEYSTORE_FILE\")"
echo "  gh secret set SIGNING_KEYSTORE_PASSWORD -b \"$PASSWORD\""
echo "  gh secret set SIGNING_KEY_ALIAS -b \"$ALIAS\""
echo "  gh secret set SIGNING_KEY_PASSWORD -b \"$PASSWORD\""
echo ""
