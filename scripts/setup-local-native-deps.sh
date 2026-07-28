#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
DEPS_DIR="$ROOT_DIR/.local-deps"
VERSIONS_FILE="$ROOT_DIR/build-logic/convention/src/main/kotlin/Versions.kt"
ECM_VERSION="6.9.0"
ECM_PREFIX="$DEPS_DIR/ecm/install"
ECM_DIR="$ECM_PREFIX/share/ECM/cmake"
GETTEXT_BIN_DIR="$DEPS_DIR/gettext/bin"

project_string_version() {
  sed -n "s/.*const val $1 = \"\([^\"]*\)\".*/\1/p" "$VERSIONS_FILE" | head -n 1
}

project_int_version() {
  sed -n "s/.*const val $1 = \([0-9][0-9]*\).*/\1/p" "$VERSIONS_FILE" | head -n 1
}

bootstrap_submodules() {
  if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Initializing native source dependencies..." >&2
    git -C "$ROOT_DIR" submodule sync --recursive >&2
    attempt=1
    while [ "$attempt" -le 3 ]; do
      if git -C "$ROOT_DIR" submodule update --init --recursive --jobs 8 >&2; then
        return 0
      fi
      if [ "$attempt" -eq 3 ]; then
        echo "Unable to initialize Git submodules after $attempt attempts." >&2
        exit 1
      fi
      echo "Submodule download failed; retrying ($attempt/3)..." >&2
      attempt=$((attempt + 1))
    done
  fi

  if [ ! -f "$ROOT_DIR/lib/fcitx5/src/main/cpp/fcitx5/CMakeLists.txt" ]; then
    echo "Native dependencies are missing. Clone this repository with Git instead of downloading a source archive." >&2
    exit 1
  fi
}

find_sdk_dir() {
  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return 0
  fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    printf '%s\n' "$ANDROID_HOME"
    return 0
  fi
  if [ -f "$ROOT_DIR/local.properties" ]; then
    sdk_dir=$(sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/local.properties" | sed 's#\\:#:#g; s#\\\\#\\#g' | head -n 1)
    if [ -n "$sdk_dir" ] && [ -d "$sdk_dir" ]; then
      printf '%s\n' "$sdk_dir"
      return 0
    fi
  fi
  return 1
}

find_sdkmanager() {
  sdk_dir=$1
  if [ -x "$sdk_dir/cmdline-tools/latest/bin/sdkmanager" ]; then
    printf '%s\n' "$sdk_dir/cmdline-tools/latest/bin/sdkmanager"
    return 0
  fi
  sdkmanager_bin=$(find "$sdk_dir/cmdline-tools" -path '*/bin/sdkmanager' -type f 2>/dev/null | sort | tail -n 1)
  if [ -n "$sdkmanager_bin" ]; then
    printf '%s\n' "$sdkmanager_bin"
    return 0
  fi
  if command -v sdkmanager >/dev/null 2>&1; then
    command -v sdkmanager
    return 0
  fi
  return 1
}

bootstrap_android_sdk() {
  ANDROID_SDK_DIR=$(find_sdk_dir || true)
  if [ -z "$ANDROID_SDK_DIR" ]; then
    echo "Android SDK not found. Install Android Studio or Android SDK Command-line Tools, then set ANDROID_HOME." >&2
    exit 1
  fi

  compile_sdk=$(project_int_version compileSdk)
  build_tools=$(project_string_version defaultBuildTools)
  ndk=$(project_string_version defaultNDK)
  cmake=$(project_string_version defaultCMake)
  if [ -z "$compile_sdk" ] || [ -z "$build_tools" ] || [ -z "$ndk" ] || [ -z "$cmake" ]; then
    echo "Unable to read pinned Android tool versions from $VERSIONS_FILE." >&2
    exit 1
  fi

  if [ -f "$ANDROID_SDK_DIR/platforms/android-$compile_sdk/android.jar" ] &&
    [ -d "$ANDROID_SDK_DIR/build-tools/$build_tools" ] &&
    [ -f "$ANDROID_SDK_DIR/ndk/$ndk/source.properties" ] &&
    [ -x "$ANDROID_SDK_DIR/cmake/$cmake/bin/cmake" ]; then
    return 0
  fi

  sdkmanager_bin=$(find_sdkmanager "$ANDROID_SDK_DIR" || true)
  if [ -z "$sdkmanager_bin" ]; then
    echo "Android SDK components are missing and sdkmanager was not found. Install Android SDK Command-line Tools and retry." >&2
    exit 1
  fi

  echo "Installing pinned Android SDK components..." >&2
  "$sdkmanager_bin" --sdk_root="$ANDROID_SDK_DIR" \
    "platforms;android-$compile_sdk" \
    "build-tools;$build_tools" \
    "ndk;$ndk" \
    "cmake;$cmake" >&2
}

find_cmake_bin() {
  if command -v cmake >/dev/null 2>&1; then
    command -v cmake
    return 0
  fi
  sdk_dir=$(find_sdk_dir || true)
  if [ -n "$sdk_dir" ] && [ -d "$sdk_dir/cmake" ]; then
    cmake_bin=$(find "$sdk_dir/cmake" -path '*/bin/cmake' -type f | sort | tail -n 1)
    if [ -n "$cmake_bin" ]; then
      printf '%s\n' "$cmake_bin"
      return 0
    fi
  fi
  return 1
}

bootstrap_ecm() {
  if [ -f "$ECM_DIR/ECMConfig.cmake" ]; then
    return 0
  fi
  cmake_bin=$(find_cmake_bin || true)
  if [ -z "$cmake_bin" ]; then
    echo "Unable to find cmake. Install Android SDK CMake or add cmake to PATH." >&2
    exit 1
  fi

  src_dir="$DEPS_DIR/src"
  archive="$src_dir/ecm-${ECM_VERSION}.tar.gz"
  unpack_dir="$src_dir/extra-cmake-modules-${ECM_VERSION}"
  mkdir -p "$src_dir"
  if [ ! -f "$archive" ]; then
    archive_tmp="${archive}.tmp"
    rm -f "$archive_tmp"
    if ! curl --fail --location --retry 5 --retry-all-errors --connect-timeout 20 --max-time 300 \
      "https://codeload.github.com/KDE/extra-cmake-modules/tar.gz/refs/tags/v${ECM_VERSION}" \
      -o "$archive_tmp"; then
      rm -f "$archive_tmp"
      echo "Unable to download ECM ${ECM_VERSION}. Check network access and retry." >&2
      exit 1
    fi
    mv "$archive_tmp" "$archive"
  fi
  rm -rf "$unpack_dir"
  tar -xzf "$archive" -C "$src_dir"
  "$cmake_bin" -S "$unpack_dir" -B "$unpack_dir/build" -DCMAKE_INSTALL_PREFIX="$ECM_PREFIX" -DBUILD_TESTING=OFF >/dev/null
  "$cmake_bin" --build "$unpack_dir/build" -j4 >/dev/null
  "$cmake_bin" --install "$unpack_dir/build" >/dev/null
}

bootstrap_gettext_wrappers() {
  mkdir -p "$GETTEXT_BIN_DIR"

  cat > "$GETTEXT_BIN_DIR/msgfmt" <<'EOF'
#!/bin/sh
set -eu
out=""
template=""
input=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o)
      out="$2"
      shift 2
      ;;
    --template)
      template="$2"
      shift 2
      ;;
    --desktop|--xml|--no-hash|--endianness=*)
      shift
      ;;
    -d)
      shift 2
      ;;
    --*)
      shift
      ;;
    *)
      input="$1"
      shift
      ;;
  esac
done
if [ -z "$out" ]; then
  echo "msgfmt stub: missing -o output" >&2
  exit 1
fi
mkdir -p "$(dirname "$out")"
if [ -n "$template" ] && [ -f "$template" ]; then
  cp "$template" "$out"
elif [ -n "$input" ] && [ -f "$input" ]; then
  root_dir=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
  python3 "$root_dir/scripts/compile_mo.py" "$input" "$out"
else
  : > "$out"
fi
EOF

  cat > "$GETTEXT_BIN_DIR/msgmerge" <<'EOF'
#!/bin/sh
set -eu
out=""
input=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o)
      out="$2"
      shift 2
      ;;
    --*)
      shift
      ;;
    *)
      if [ -z "$input" ]; then
        input="$1"
      fi
      shift
      ;;
  esac
done
if [ -z "$out" ] || [ -z "$input" ]; then
  echo "msgmerge stub: expected input and -o output" >&2
  exit 1
fi
mkdir -p "$(dirname "$out")"
cp "$input" "$out"
EOF

  chmod +x "$GETTEXT_BIN_DIR/msgfmt" "$GETTEXT_BIN_DIR/msgmerge"
}

bootstrap_submodules
bootstrap_android_sdk
bootstrap_ecm
bootstrap_gettext_wrappers

echo "ECM_DIR=$ECM_DIR"
echo "GETTEXT_BIN_DIR=$GETTEXT_BIN_DIR"
echo "ANDROID_HOME=$ANDROID_SDK_DIR"
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_DIR"