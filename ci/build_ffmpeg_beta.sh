#!/usr/bin/env bash
set -euo pipefail
ROOT="$(pwd)"
MEDIA3_REF=96802c557266dd026e0fb36e0dc4e8b169b13b36
FFMPEG_REF=3f92512fd1fd6f5e6d6eb45a156c352835314d69
NDK_VERSION=28.2.13676358
SDKMANAGER="$(find "$ANDROID_HOME/cmdline-tools" -name sdkmanager -type f -print -quit)"
"$SDKMANAGER" "ndk;$NDK_VERSION" 'cmake;3.22.1'
sudo apt-get update
sudo apt-get install -y make nasm pkg-config yasm
WORK="$(mktemp -d)"
git init "$WORK/media"
git -C "$WORK/media" remote add origin https://github.com/androidx/media.git
git -C "$WORK/media" fetch --depth 1 origin "$MEDIA3_REF"
git -C "$WORK/media" checkout --detach FETCH_HEAD
test "$(git -C "$WORK/media" rev-parse HEAD)" = "$MEDIA3_REF"
MODULE="$WORK/media/libraries/decoder_ffmpeg"
git init "$MODULE/src/main/jni/ffmpeg"
git -C "$MODULE/src/main/jni/ffmpeg" remote add origin https://github.com/FFmpeg/FFmpeg.git
git -C "$MODULE/src/main/jni/ffmpeg" fetch --depth 1 origin "$FFMPEG_REF"
git -C "$MODULE/src/main/jni/ffmpeg" checkout --detach FETCH_HEAD
test "$(git -C "$MODULE/src/main/jni/ffmpeg" rev-parse HEAD)" = "$FFMPEG_REF"
printf '\nandroid { ndkVersion = "%s" }\n' "$NDK_VERSION" >> "$MODULE/build.gradle.kts"
(cd "$MODULE/src/main/jni" && ./build_ffmpeg.sh "$MODULE/src/main" "$ANDROID_HOME/ndk/$NDK_VERSION" linux-x86_64 24 ac3 eac3 dca mlp truehd)
# Maven Central may return transient 429 while resolving Kotlin dependencies.
# Keep the pinned upstream revision and compiler; retry only transient download failures.
# Use Maven Central's alternate official endpoint for JetBrains dependencies.
# Restrict this to the upstream FFmpeg build, preserving all pinned versions.
cat > "$WORK/central-fallback.gradle" <<'GRADLE'
gradle.beforeSettings { settings ->
    settings.pluginManagement.repositories {
        maven {
            url = uri('https://repo1.maven.org/maven2')
            content { includeGroupByRegex('org[.]jetbrains([.].*)?') }
        }
        gradlePluginPortal()
    }
}
gradle.beforeProject { project ->
    project.buildscript.repositories {
        maven {
            url = uri('https://repo1.maven.org/maven2')
            content { includeGroupByRegex('org[.]jetbrains([.].*)?') }
        }
    }
}
GRADLE
for attempt in 1 2 3 4; do
  if (cd "$WORK/media" && ./gradlew -I "$WORK/central-fallback.gradle" :lib-decoder-ffmpeg:assembleRelease --no-daemon) 2>&1 | tee "$WORK/ffmpeg-gradle.log"; then
    break
  fi
  if [ "$attempt" = 4 ] || ! grep -Eqi '429|Too Many Requests|502|503|504|Read timed out|Connection reset' "$WORK/ffmpeg-gradle.log"; then
    exit 1
  fi
  echo "Transient dependency download failure; retrying exact FFmpeg build"
  sleep "$((attempt * 60))"
done
aar="$(find "$WORK/media" -path '*decoder_ffmpeg*' -name '*-release.aar' -type f -print -quit)"
test -n "$aar"
mkdir -p "$ROOT/app/libs" "$WORK/inspect" "$ROOT/app/src/main/assets/licenses" "$WORK/source"
cp "$aar" "$ROOT/app/libs/media3-ffmpeg-1.12.0-rc01.aar"
unzip -q "$aar" -d "$WORK/inspect"
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
  test -f "$WORK/inspect/jni/$abi/libffmpegJNI.so"
done
for abi in arm64-v8a x86_64; do
  align=$("$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -lW "$WORK/inspect/jni/$abi/libffmpegJNI.so" | awk '$1=="LOAD"{print $NF}')
  echo "$abi LOAD alignment: $align"
  test -n "$align"
  if grep -qv '^0x4000$' <<<"$align"; then exit 1; fi
done
git -C "$WORK/media" archive --format=tar.gz --prefix=media3/ "$MEDIA3_REF" > "$WORK/source/media3.tar.gz"
git -C "$MODULE/src/main/jni/ffmpeg" archive --format=tar.gz --prefix=ffmpeg/ "$FFMPEG_REF" > "$WORK/source/ffmpeg.tar.gz"
cp "$ROOT/ci/build_ffmpeg_beta.sh" "$WORK/source/"
cp "$MODULE/src/main/jni/ffmpeg/COPYING.LGPLv2.1" "$ROOT/app/src/main/assets/licenses/FFmpeg-LGPL-2.1.txt"
cp "$WORK/media/LICENSE" "$ROOT/app/src/main/assets/licenses/Media3-Apache-2.0.txt"
cat > "$ROOT/app/src/main/assets/licenses/FFmpeg-NOTICE.txt" <<'EOF'
This app includes FFmpeg 6.0 source revision 3f92512fd1fd6f5e6d6eb45a156c352835314d69
under LGPL 2.1 or later and AndroidX Media3 FFmpeg JNI under Apache 2.0.
Corresponding source and build recipe: https://github.com/sharkmantv/SharkNailTV/releases/tag/v5.0.27
Asset: ffmpeg-source.tar.gz. Audio decoders only: ac3 eac3 dca mlp truehd.
You may modify and rebuild the LGPL library and JNI wrapper, replace libffmpegJNI.so
for your ABI in the APK, then align and sign with your own key for personal installation.
Reverse engineering for debugging modifications to the LGPL library is permitted.
EOF
cp "$ROOT/app/src/main/assets/licenses/"*.txt "$WORK/source/"
tar -czf "$ROOT/ffmpeg-source.tar.gz" -C "$WORK/source" .
sha256sum "$ROOT/app/libs/media3-ffmpeg-1.12.0-rc01.aar"
