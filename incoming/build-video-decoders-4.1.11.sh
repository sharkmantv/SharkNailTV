#!/usr/bin/env bash
set -euo pipefail
: "${SOURCE_DIR:?}" "${RUNNER_TEMP:?}" "${ANDROID_HOME:?}"
src="$RUNNER_TEMP/shark-media3-decoder-source"
git clone --depth 1 --branch 1.11.1 https://github.com/androidx/media.git "$src"
test "$(git -C "$src" describe --tags --exact-match)" = 1.11.1
sdkmanager="$(find "$ANDROID_HOME/cmdline-tools" -type f -name sdkmanager -print -quit)"
"$sdkmanager" 'platforms;android-36' 'ndk;27.2.12479018' 'ndk;29.0.14206865' 'cmake;3.22.1'
sudo apt-get update
sudo apt-get install -y meson ninja-build nasm
av1="$src/libraries/decoder_av1/src/main"
vp9="$src/libraries/decoder_vp9/src/main"
git clone --depth 1 --branch v0.9.0 https://github.com/google/cpu_features.git "$av1/jni/cpu_features"
git clone --depth 1 --branch 1.5.3 https://github.com/videolan/dav1d.git "$av1/jni/dav1d"
git clone --depth 1 --branch v1.15.2 https://github.com/webmproject/libvpx.git "$vp9/jni/libvpx"
(
  cd "$av1/jni"
  bash build_dav1d.sh "$av1" "$ANDROID_HOME/ndk/27.2.12479018" linux-x86_64
)
(
  cd "$vp9/jni"
  bash generate_libvpx_android_configs.sh
  "$ANDROID_HOME/ndk/29.0.14206865/ndk-build" -j4
)
(
  cd "$src"
  ./gradlew --no-daemon :lib-decoder-av1:assembleRelease :lib-decoder-vp9:assembleRelease
)
mkdir -p "$SOURCE_DIR/app/libs" "$SOURCE_DIR/app/src/main/assets/decoder-licenses"
for codec in av1 vp9; do
  mapfile -t aars < <(find "$src/libraries/decoder_$codec" -path '*/outputs/aar/*release.aar')
  [[ ${#aars[@]} == 1 ]]
  unzip -tq "${aars[0]}"
  cp "${aars[0]}" "$SOURCE_DIR/app/libs/shark-media3-decoder-$codec.aar"
done
license="$SOURCE_DIR/app/src/main/assets/decoder-licenses"
cp "$src/LICENSE" "$license/media3-Apache-2.0.txt"
cp "$av1/jni/dav1d/COPYING" "$license/dav1d-COPYING.txt"
cp "$av1/jni/cpu_features/LICENSE" "$license/cpu-features-LICENSE.txt"
cp "$vp9/jni/libvpx/LICENSE" "$license/libvpx-LICENSE.txt"
cp "$vp9/jni/libvpx/PATENTS" "$license/libvpx-PATENTS.txt"
printf '%s\n' 'Media3 1.11.1: https://github.com/androidx/media/tree/1.11.1' 'dav1d 1.5.3: https://github.com/videolan/dav1d/tree/1.5.3' 'cpu_features v0.9.0: https://github.com/google/cpu_features/tree/v0.9.0' 'libvpx v1.15.2: https://github.com/webmproject/libvpx/tree/v1.15.2' > "$license/SOURCES.txt"
python - "$SOURCE_DIR/app/build.gradle.kts" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1])
s=p.read_text()
assert 'media3Version = "1.11.1"' in s
# Avoid duplicate file dependencies where the original project uses a broad fileTree.
if 'include("*.aar", "*.jar")' not in s:
    s=s.replace('dependencies {','dependencies {\n    implementation(files("libs/shark-media3-decoder-av1.aar", "libs/shark-media3-decoder-vp9.aar"))',1)
p.write_text(s)
PY
