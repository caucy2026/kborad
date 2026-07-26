#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
DEPS_DIR="$ROOT_DIR/.local-deps"
ECM_VERSION="6.9.0"
ECM_PREFIX="$DEPS_DIR/ecm/install"
ECM_DIR="$ECM_PREFIX/share/ECM/cmake"
GETTEXT_BIN_DIR="$DEPS_DIR/gettext/bin"

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
    curl -L "https://github.com/KDE/extra-cmake-modules/archive/refs/tags/v${ECM_VERSION}.tar.gz" -o "$archive"
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

bootstrap_ecm
bootstrap_gettext_wrappers

echo "ECM_DIR=$ECM_DIR"
echo "GETTEXT_BIN_DIR=$GETTEXT_BIN_DIR"