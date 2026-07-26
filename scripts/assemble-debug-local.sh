#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
DEPS_ENV=$(mktemp "${TMPDIR:-/tmp}/kemi-native-deps.XXXXXX")
trap 'rm -f "$DEPS_ENV"' EXIT HUP INT TERM

if ! "$ROOT_DIR/scripts/setup-local-native-deps.sh" > "$DEPS_ENV"; then
	echo "Unable to prepare local native dependencies." >&2
	exit 1
fi
eval "$(sed 's/^/export /' "$DEPS_ENV")"

cd "$ROOT_DIR"
PATH="$GETTEXT_BIN_DIR:$PATH" ECM_DIR="$ECM_DIR" ./gradlew :app:assembleDebug "$@"