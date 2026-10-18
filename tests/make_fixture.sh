#!/usr/bin/env bash
# Rebuild tests/data/fixture.apk from tests/fixture/.
#
# The result is checked in, so this only has to run when the fixture itself
# changes -- the tests need no toolchain, only Python.
#
#   tests/make_fixture.sh [path/to/aapt2] [path/to/android.jar]
set -euo pipefail
cd "$(dirname "$0")"
AAPT2="${1:-aapt2}"
ANDROID_JAR="${2:-../tools/android.jar}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
"$AAPT2" compile --dir fixture/res -o "$TMP/res.zip"
"$AAPT2" link -o data/fixture.apk -I "$ANDROID_JAR" \
    --manifest fixture/AndroidManifest.xml "$TMP/res.zip"
echo "tests/data/fixture.apk rebuilt"
