#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
DEPS_ENV=$(mktemp "${TMPDIR:-/tmp}/kemi-native-deps.XXXXXX")
trap 'rm -f "$DEPS_ENV"' EXIT HUP INT TERM

if [ -z "${SIGN_KEY_PWD:-}" ] || [ -z "${SIGN_KEY_ALIAS:-}" ]; then
	printf '%s\n' 'SIGN_KEY_PWD and SIGN_KEY_ALIAS are required for a signed release build.' >&2
	exit 1
fi

if [ -z "${SIGN_KEY_FILE:-}" ] && [ -z "${SIGN_KEY_BASE64:-}" ]; then
	printf '%s\n' 'Set either SIGN_KEY_FILE or SIGN_KEY_BASE64 for the release signing key.' >&2
	exit 1
fi

if ! "$ROOT_DIR/scripts/setup-local-native-deps.sh" > "$DEPS_ENV"; then
	printf '%s\n' 'Unable to prepare local native dependencies.' >&2
	exit 1
fi
eval "$(sed 's/^/export /' "$DEPS_ENV")"

cd "$ROOT_DIR"
BUILD_ABI=arm64-v8a PATH="$GETTEXT_BIN_DIR:$PATH" ECM_DIR="$ECM_DIR" ./gradlew :app:assembleRelease "$@"

APK=$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*-arm64-v8a-release.apk' ! -name '*-unsigned.apk' -print -quit)
if [ -z "$APK" ]; then
	printf '%s\n' 'No signed arm64-v8a release APK was produced.' >&2
	exit 1
fi

mkdir -p build
cp "$APK" build/kboard.apk
printf 'Release APK: %s\n' "$ROOT_DIR/build/kboard.apk"