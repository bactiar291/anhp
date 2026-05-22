#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
PLATFORM="$SDK/platforms/android-36/android.jar"
BT="$SDK/build-tools/35.0.0"
BUILD="$ROOT/build"
APP="$ROOT/app/src/main"
OUT="$ROOT/release"

if [ ! -f "$PLATFORM" ]; then
  echo "android.jar not found: $PLATFORM" >&2
  exit 1
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/generated" "$BUILD/classes" "$BUILD/dex" "$OUT"

"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res/resources.zip"
"$BT/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/generated" \
  --auto-add-overlay \
  -o "$BUILD/unsigned.apk" \
  "$BUILD/res/resources.zip"

find "$APP/java" "$BUILD/generated" -name '*.java' | sort > "$BUILD/sources.txt"
javac -source 8 -target 8 \
  -bootclasspath "$PLATFORM" \
  -encoding UTF-8 \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt"

jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$BT/d8" --release --min-api 26 --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/unsigned.apk" "$BUILD/unsigned-dex.apk"
jar uf "$BUILD/unsigned-dex.apk" -C "$BUILD/dex" classes.dex

"$BT/zipalign" -f 4 "$BUILD/unsigned-dex.apk" "$BUILD/aligned.apk"

KEYSTORE="$BUILD/anhp-debug.keystore"
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass android \
  -keypass android \
  -alias anhp \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=AnHP,O=AnHP,C=ID" >/dev/null

"$BT/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUT/AnHP-release.apk" \
  "$BUILD/aligned.apk"

"$BT/apksigner" verify "$OUT/AnHP-release.apk"
echo "Built $OUT/AnHP-release.apk"
