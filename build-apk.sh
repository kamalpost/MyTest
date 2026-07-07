#!/usr/bin/env bash
# Builds app-debug.apk WITHOUT Gradle/AGP — useful where Google's SDK and
# Maven servers are unreachable. Requires:
#   TOOLS dir containing: aapt2, r8lib.jar (d8), kotlin-compiler-embeddable.jar,
#                         kotlin-stdlib.jar, annotations.jar, apksig.jar
#   ANDROID_JAR pointing at platforms/android-30/android.jar
# See README "Building" for where each piece can be downloaded.
set -euo pipefail

TOOLS=${TOOLS:-/opt/buildtools}
ANDROID_JAR=${ANDROID_JAR:-/opt/android-sdk/platforms/android-30/android.jar}
SRC=app/src/main
PKG=com.swingtrader.sp500
OUT=build-manual
KEYSTORE=$OUT/debug.p12

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> aapt2 compile"
"$TOOLS/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "==> aapt2 link"
# The manifest keeps AGP-style namespace (no package attr); inject it for aapt2.
sed "s|<manifest |<manifest package=\"$PKG\" |" "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$TOOLS/aapt2" link \
    -o "$OUT/app.unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$OUT/AndroidManifest.xml" \
    -A "$SRC/assets" \
    --min-sdk-version 26 --target-sdk-version 29 \
    --version-code 1 --version-name 1.0 \
    --java "$OUT/gen" \
    --auto-add-overlay \
    "$OUT/res.zip"

echo "==> javac R.java"
javac -source 17 -target 17 -cp "$ANDROID_JAR" -d "$OUT/classes" \
    "$OUT/gen/$(echo $PKG | tr . /)/R.java"

echo "==> kotlinc"
java -Xmx2g -cp "$TOOLS/kotlin-compiler-embeddable.jar:$TOOLS/kotlin-stdlib.jar:$TOOLS/annotations.jar:$TOOLS/trove4j.jar" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -classpath "$ANDROID_JAR:$OUT/classes:$TOOLS/kotlin-stdlib.jar:$TOOLS/annotations.jar" \
    -jvm-target 17 -no-stdlib -no-reflect \
    -d "$OUT/classes" \
    $(find "$SRC/java" -name '*.kt')

echo "==> d8"
java -cp "$TOOLS/r8lib.jar" com.android.tools.r8.D8 \
    --release --min-api 26 \
    --lib "$ANDROID_JAR" \
    --output "$OUT/dex" \
    $(find "$OUT/classes" -name '*.class') \
    "$TOOLS/kotlin-stdlib.jar" "$TOOLS/annotations.jar"

echo "==> package dex into apk"
python3 - "$OUT/app.unsigned.apk" "$OUT/dex/classes.dex" <<'EOF'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
EOF

echo "==> keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 \
        -storepass android -keypass android -alias debug \
        -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000
fi

echo "==> sign (apksig)"
javac -cp "$TOOLS/apksig.jar" -d "$OUT" tools/SignApk.java
# apksig 2.3.0 reaches into sun.security internals; open them on modern JDKs.
java --add-exports java.base/sun.security.x509=ALL-UNNAMED \
     --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
     --add-exports java.base/sun.security.util=ALL-UNNAMED \
     -cp "$TOOLS/apksig.jar:$OUT" SignApk \
    "$KEYSTORE" android debug "$OUT/app.unsigned.apk" "$OUT/app-debug.apk"

ls -la "$OUT/app-debug.apk"
echo "OK: $OUT/app-debug.apk"
