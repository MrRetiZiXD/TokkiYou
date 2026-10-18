#!/bin/sh
# The patcher's own release: built by Gradle, shrunk, then signed with the key
# the mods' releases carry -- which is how it tells a mod on the phone from
# the app that mod was made out of.
set -e
here=$(cd "$(dirname "$0")" && pwd)
: "${JAVA_HOME:=$HOME/.zcode/android-build/jdk-17.0.20.1+1}"
export JAVA_HOME
gradle=${GRADLE:-$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle | head -1)}
cd "$here"
"$gradle" -q :app:assembleRelease
out="$here/../build/patcher"
mkdir -p "$out"
version=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | cut -d'"' -f2)
cp app/build/outputs/apk/release/app-release-unsigned.apk "$out/margy-patcher-$version.apk"
java -jar "$here/../tools/uber-apk-signer.jar" -a "$out/margy-patcher-$version.apk" --allowResign --overwrite > /dev/null
echo "$out/margy-patcher-$version.apk"
