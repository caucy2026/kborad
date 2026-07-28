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
GRADLE_COMMAND=${KEMI_GRADLE_COMMAND:-./gradlew}
GRADLE_EXTRA_ARGS=${KEMI_GRADLE_EXTRA_ARGS:-}
attempt=1
while [ "$attempt" -le 3 ]; do
	# shellcheck disable=SC2086
	if PATH="$GETTEXT_BIN_DIR:$PATH" ECM_DIR="$ECM_DIR" "$GRADLE_COMMAND" :app:assembleDebug $GRADLE_EXTRA_ARGS "$@"; then
		exit 0
	fi
	if [ "$attempt" -eq 3 ]; then
		echo "Gradle build failed after $attempt attempts." >&2
		exit 1
	fi
	echo "Gradle build failed; retrying ($attempt/3)..." >&2
	attempt=$((attempt + 1))
done