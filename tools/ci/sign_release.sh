#!/usr/bin/env bash
# All secrets are supplied only by the protected GitHub release environment.
set -euo pipefail
candidate="${1:?Unsigned release APK required}"
: "${KEYSTORE_BASE64:?Original release keystore secret is required}"
: "${KEYSTORE_PASSWORD:?Keystore password is required}"
: "${KEY_ALIAS:?Signing alias is required}"
: "${KEY_PASSWORD:?Key password is required}"
[[ "${GITHUB_ACTOR:-}" == nirajmahajan ]] || { echo 'Release publication requires repository owner dispatch'; exit 1; }
keyfile="$(mktemp)"
trap 'rm -f "$keyfile"' EXIT
chmod 600 "$keyfile"
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$keyfile"
mkdir -p release
build_tools="$ANDROID_HOME/build-tools/36.0.0"
"$build_tools/zipalign" -f -p 4 "$candidate" release/judgement-aligned.apk
"$build_tools/apksigner" sign --ks "$keyfile" --ks-key-alias "$KEY_ALIAS" --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD --out release/judgement-scoreboard.apk release/judgement-aligned.apk
rm release/judgement-aligned.apk
"$build_tools/apksigner" verify --verbose --print-certs release/judgement-scoreboard.apk > release/certificate.txt
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' release/certificate.txt | tr '[:upper:]' '[:lower:]' | tr -d ':')"
expected=93e366b93ccd1c531e9a282a096ccaa9bbd719d9b8c1271e7e5384f7484bda50
[[ "$actual" == "$expected" ]] || { echo 'Certificate does not match bundled original APK'; exit 1; }
version_code="$(apkanalyzer manifest version-code release/judgement-scoreboard.apk)"
[[ "$version_code" =~ ^[0-9]+$ && "$version_code" -gt 2 ]] || { echo 'Release version code must exceed original version2'; exit 1; }
apkanalyzer manifest print release/judgement-scoreboard.apk > release/manifest.xml
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
manifest = ET.fromstring(Path('release/manifest.xml').read_text())
assert manifest.get('package') == 'android.example.judgement'
assert manifest.get('{http://schemas.android.com/apk/res/android}sharedUserId') == 'com.example.nirajmahajan64725'
PY
(cd release && sha256sum judgement-scoreboard.apk > SHA256SUMS)
